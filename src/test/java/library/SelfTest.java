package library;

import library.config.AppConfig;
import library.db.Database;
import library.model.Book;
import library.model.BookField;
import library.model.BookInput;
import library.model.IssueFilter;
import library.model.IssueStatus;
import library.model.IssueView;
import library.model.Member;
import library.model.MemberInput;
import library.model.Stats;
import library.service.FineCalculator;
import library.service.LibraryException;
import library.service.LibraryService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Automated checks for the library rules. Run with:  ./gradlew selfTest
 * It needs MySQL running, creates a throw-away database "library_selftest" and deletes it at the end.
 * (Plain Java on purpose: no testing library to install.)
 */
public final class SelfTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        config.setDbName("library_selftest");
        Database db = new Database(config);
        try {
            fineCalculator();
            settingsFile();
            try {
                db.dropDatabase();
            } catch (Exception e) {
                System.out.println("Cannot reach MySQL at " + config.dbHost() + ":" + config.dbPort()
                        + " as user '" + config.dbUser() + "': " + e.getMessage());
                System.out.println("Start MySQL (and put the password in library.properties) and try again.");
                System.exit(2);
            }
            databaseChecks(db, config);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  unexpected error: " + t);
            t.printStackTrace(System.out);
        } finally {
            try {
                db.dropDatabase();
            } catch (Exception ignored) {
                // nothing more to do
            }
        }
        System.out.println();
        System.out.println(passed + " checks passed, " + failed + " failed.");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ sections

    private static void fineCalculator() {
        section("Fine calculator");
        BigDecimal two = new BigDecimal("2");
        check("returned on the due date: 0 days late", FineCalculator.daysLate(TODAY, TODAY) == 0);
        check("returned early: 0 days late", FineCalculator.daysLate(TODAY, TODAY.minusDays(5)) == 0);
        check("3 days after the due date: 3 days late", FineCalculator.daysLate(TODAY, TODAY.plusDays(3)) == 3);
        check("no fine when on time", FineCalculator.fine(TODAY, TODAY, two).signum() == 0);
        check("10 days late at 2 per day = 20.00", FineCalculator.fine(10, two).compareTo(new BigDecimal("20.00")) == 0);
        check("fine is rounded to 2 decimals", FineCalculator.fine(3, new BigDecimal("1.505")).toPlainString().equals("4.52"));
    }

    private static void settingsFile() throws Exception {
        section("Settings file");
        Path file = Files.createTempFile("library", ".properties");
        try {
            Files.writeString(file, "db.port=abc\nloan.days=0\nfine.per.day=-5\nmax.books.per.member=x\ncurrency=Rs.\n",
                    StandardCharsets.UTF_8);
            AppConfig c = AppConfig.load(file);
            check("bad numbers fall back to the defaults",
                    c.dbPort() == 3306 && c.loanDays() == 14 && c.maxBooksPerMember() == 3
                            && c.finePerDay().compareTo(new BigDecimal("2")) == 0);
            check("currency is read", c.currency().equals("Rs."));
            c.setConnection("db.example.org", 3307, "reader", "p@ss:w=rd #1 é");
            c.save();
            AppConfig again = AppConfig.load(file);
            check("saved connection details are read back exactly",
                    again.dbHost().equals("db.example.org") && again.dbPort() == 3307
                            && again.dbUser().equals("reader") && again.dbPassword().equals("p@ss:w=rd #1 é"));
            check("missing file gives the defaults", AppConfig.load(Path.of("no-such-file.properties")).dbUser().equals("root"));
            check("money is formatted", AppConfig.load(file).money(new BigDecimal("12")).equals("Rs.12.00"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void databaseChecks(Database db, AppConfig config) throws Exception {
        section("Database set-up");
        check("first start creates the database", db.initialize(TODAY));
        LibraryService svc = new LibraryService(db, config, at(TODAY));
        Stats s = svc.stats();
        check("sample data: 15 titles", s.titles() == 15);
        check("sample data: 31 copies, 28 available", s.totalCopies() == 31 && s.availableCopies() == 28);
        check("sample data: 6 active members", s.activeMembers() == 6);
        check("sample data: 3 on loan, 2 overdue", s.onLoan() == 3 && s.overdue() == 2);
        check("sample data: 12.00 in fines collected", s.finesCollected().compareTo(new BigDecimal("12")) == 0);
        check("second start keeps the data and adds nothing", !db.initialize(TODAY) && svc.stats().titles() == 15);

        section("Searching books");
        check("title 'clean'", titles(svc.searchBooks("clean", BookField.TITLE)).equals(List.of("Clean Code")));
        check("search ignores upper/lower case", titles(svc.searchBooks("CLEAN", BookField.TITLE)).equals(List.of("Clean Code")));
        check("author 'hawking'", titles(svc.searchBooks("hawking", BookField.AUTHOR)).equals(List.of("A Brief History of Time")));
        check("ISBN '9780735'", titles(svc.searchBooks("9780735", BookField.ISBN)).equals(List.of("Atomic Habits")));
        check("category 'fiction' finds 5 books", svc.searchBooks("fiction", BookField.CATEGORY).size() == 5);
        check("any field 'java' finds both Java books",
                sorted(titles(svc.searchBooks("java", BookField.ANY))).equals(List.of("Effective Java", "Head First Java")));
        check("an empty search lists all 15", svc.searchBooks("", BookField.ANY).size() == 15);
        check("a typed % is not a wildcard", svc.searchBooks("%", BookField.ANY).isEmpty());
        check("an author with an accent: 'Héctor García'", titles(svc.searchBooks("García", BookField.AUTHOR)).equals(List.of("Ikigai")));
        Book clean = svc.searchBooks("clean", BookField.TITLE).get(0);
        check("Clean Code: 1 copy, none available", clean.totalCopies() == 1 && clean.availableCopies() == 0);

        section("Searching members");
        check("by member ID '3'", names(svc.searchMembers("3")).equals(List.of("Rohan Verma")));
        check("by name 'kaur'", names(svc.searchMembers("kaur")).equals(List.of("Simran Kaur")));
        check("by phone", names(svc.searchMembers("9876500005")).equals(List.of("Harpreet Gill")));
        check("by email text", svc.searchMembers("example.com").size() == 6);
        check("an empty search lists all 7", svc.searchMembers("").size() == 7);

        section("Adding and editing books");
        expectError("a title is required", () -> svc.saveBook(book(0, "  ", "A", "", "C", null, 1)), "title");
        expectError("an author is required", () -> svc.saveBook(book(0, "T", "", "", "C", null, 1)), "author");
        expectError("a category is required", () -> svc.saveBook(book(0, "T", "A", "", " ", null, 1)), "category");
        expectError("a bad ISBN is refused", () -> svc.saveBook(book(0, "T", "A", "12-ab", "C", null, 1)), "isbn");
        expectError("a duplicate ISBN is refused", () -> svc.saveBook(book(0, "T", "A", "9780132350884", "C", null, 1)),
                "already has that isbn");
        expectError("a year far in the future is refused", () -> svc.saveBook(book(0, "T", "A", "", "C", 3000, 1)), "year");
        expectError("at least one copy", () -> svc.saveBook(book(0, "T", "A", "", "C", null, 0)), "at least 1 copy");
        Book added = svc.saveBook(book(0, "  The   Pragmatic Programmer ", "Andrew Hunt", "978-0-201-61622-4", "Programming", 1999, 2));
        check("extra spaces are cleaned from the title", added.title().equals("The Pragmatic Programmer"));
        check("a new book has all its copies available", added.availableCopies() == 2 && added.issuedCopies() == 0);
        check("an existing category is reused, not duplicated",
                Collections.frequency(svc.categories(), "Programming") == 1);
        check("a brand-new category is created", svc.saveBook(book(0, "Cosmos", "Carl Sagan", "", "Astronomy", 1980, 1)).category().equals("Astronomy")
                && svc.categories().contains("Astronomy"));
        Book unicode = svc.saveBook(book(0, "Ñandú – 学习 ₹", "Åsa Ünal", "", "Fiction", null, 1));
        check("accents, Chinese and ₹ survive the database",
                titles(svc.searchBooks("学习", BookField.TITLE)).equals(List.of("Ñandú – 学习 ₹"))
                        && svc.getBook(unicode.id()).author().equals("Åsa Ünal"));
        Book renamed = svc.saveBook(book(added.id(), "The Pragmatic Programmer", "David Thomas", "", "Programming", 2019, 3));
        check("editing changes the author, year and copies",
                renamed.author().equals("David Thomas") && renamed.year() == 2019 && renamed.totalCopies() == 3 && renamed.isbn() == null);

        section("Adding and editing members");
        expectError("a name is required", () -> svc.saveMember(new MemberInput(0, " ", "9999900001", "", true)), "name");
        expectError("the phone needs 10 digits", () -> svc.saveMember(new MemberInput(0, "X", "12345", "", true)), "10 digits");
        expectError("a bad email is refused", () -> svc.saveMember(new MemberInput(0, "X", "9999900001", "nope", true)), "email");
        expectError("a duplicate email is refused",
                () -> svc.saveMember(new MemberInput(0, "X", "9999900001", "aarav.sharma@example.com", true)), "already uses that email");
        Member tester = svc.saveMember(new MemberInput(0, "Test User", "9999900001", "", true));
        check("a member without email is saved with no email", tester.email() == null && tester.active());
        check("the join date is today", tester.joinedOn().equals(TODAY));
        check("two members without email do not clash",
                svc.saveMember(new MemberInput(0, "Second User", "9999900002", "", true)).email() == null);

        section("Issuing books");
        IssueView first = svc.issueBook(bookId(svc, "Atomic Habits"), tester.id());
        check("issuing works and the due date is 14 days away", first.issue().dueOn().equals(TODAY.plusDays(14))
                && first.issue().issuedOn().equals(TODAY) && first.status() == IssueStatus.ON_LOAN);
        check("one copy fewer is available", svc.getBook(bookId(svc, "Atomic Habits")).availableCopies() == 1);
        expectError("the same member cannot take the same book twice",
                () -> svc.issueBook(bookId(svc, "Atomic Habits"), tester.id()), "already has a copy");
        expectError("a book with no free copy cannot be issued",
                () -> svc.issueBook(clean.id(), tester.id()), "no copy");
        Member inactive = svc.searchMembers("Karan").get(0);
        expectError("an inactive member cannot borrow", () -> svc.issueBook(clean.id() + 1, inactive.id()), "inactive");
        expectError("an unknown book ID is refused", () -> svc.issueBook(9999, tester.id()), "no book with id 9999");
        expectError("an unknown member ID is refused", () -> svc.issueBook(clean.id(), 9999), "no member with id 9999");
        IssueView second = svc.issueBook(bookId(svc, "Effective Java"), tester.id());
        IssueView third = svc.issueBook(bookId(svc, "Head First Java"), tester.id());
        expectError("a member cannot have more than 3 books",
                () -> svc.issueBook(bookId(svc, "A Brief History of Time"), tester.id()), "limit");
        check("the member's 'books out' count is 3", svc.getMember(tester.id()).booksOut() == 3);

        section("Returning books and fines");
        IssueView onTime = svc.returnBook(third.issue().id());
        check("returned on the same day: no fine", onTime.fine().signum() == 0 && onTime.status() == IssueStatus.RETURNED
                && onTime.issue().returnedOn().equals(TODAY));
        check("the copy is available again", svc.getBook(bookId(svc, "Head First Java")).availableCopies() == 2);
        expectError("a book cannot be returned twice", () -> svc.returnBook(third.issue().id()), "already returned");

        IssueView openOverdue = svc.listIssues(IssueFilter.OVERDUE, null).stream()
                .filter(v -> v.issue().bookTitle().equals("Clean Code")).findFirst().orElseThrow();
        check("an overdue record shows 6 days late and 12.00 owed",
                openOverdue.status() == IssueStatus.OVERDUE && openOverdue.daysLate() == 6
                        && openOverdue.fine().compareTo(new BigDecimal("12")) == 0);
        svc.previewReturn(openOverdue.issue().id());
        check("previewing a return changes nothing", svc.listIssues(IssueFilter.OVERDUE, null).size() == 2);

        LibraryService later = new LibraryService(db, config, at(TODAY.plusDays(20)));
        IssueView late = later.returnBook(second.issue().id());
        check("returned 6 days after the due date: fine 12.00 (6 x 2)",
                late.daysLate() == 6 && late.fine().compareTo(new BigDecimal("12")) == 0
                        && late.issue().returnedOn().equals(TODAY.plusDays(20)));
        check("the fine is stored with the record", later.listIssues(IssueFilter.RETURNED, tester.id()).stream()
                .anyMatch(v -> v.issue().id() == second.issue().id() && v.fine().compareTo(new BigDecimal("12")) == 0));
        check("fines collected add up (12 + 12 = 24)", later.stats().finesCollected().compareTo(new BigDecimal("24")) == 0);
        IssueView lateSample = svc.returnBook(openOverdue.issue().id());
        check("the overdue sample book comes back with its 12.00 fine", lateSample.fine().compareTo(new BigDecimal("12")) == 0);
        check("Clean Code is available again", svc.getBook(clean.id()).availableCopies() == 1);

        section("Lists and numbers agree");
        Stats now = svc.stats();
        check("'on loan' on the dashboard matches the list", now.onLoan() == svc.listIssues(IssueFilter.ON_LOAN, null).size());
        check("'overdue' on the dashboard matches the list", now.overdue() == svc.listIssues(IssueFilter.OVERDUE, null).size());
        check("available = copies - on loan", now.availableCopies() == now.totalCopies() - now.onLoan());
        check("filtering the records by member ID works",
                svc.listIssues(IssueFilter.ALL, tester.id()).size() == 3 && svc.listIssues(IssueFilter.ON_LOAN, tester.id()).size() == 1);
        List<IssueView> everything = svc.listIssues(IssueFilter.ALL, null);
        check("books still out are listed before returned ones",
                everything.stream().map(v -> v.issue().isReturned()).toList().indexOf(true)
                        > everything.stream().map(v -> v.issue().isReturned()).toList().lastIndexOf(false));

        section("Editing and deleting with issue records");
        int atomic = bookId(svc, "Atomic Habits");
        expectError("copies cannot drop below the number on loan",
                () -> svc.saveBook(book(atomic, "Atomic Habits", "James Clear", "9780735211292", "Self-help", 2018, 1)), "out on loan");
        check("copies can drop to exactly the number on loan",
                svc.saveBook(book(atomic, "Atomic Habits", "James Clear", "9780735211292", "Self-help", 2018, 2)).totalCopies() == 2);
        expectError("a book with issue records cannot be deleted", () -> svc.deleteBook(clean.id()), "cannot be deleted");
        svc.deleteBook(added.id());
        check("a book with no records can be deleted", svc.getBook(added.id()) == null);
        Member aarav = svc.searchMembers("Aarav").get(0);
        expectError("a member with issue records cannot be deleted", () -> svc.deleteMember(aarav.id()), "inactive instead");
        Member temp = svc.saveMember(new MemberInput(0, "Temp Person", "9999900003", "temp@example.com", true));
        svc.deleteMember(temp.id());
        check("a member with no records can be deleted", svc.getMember(temp.id()) == null);
        svc.saveMember(new MemberInput(tester.id(), "Test User", "9999900001", "", false));
        expectError("marking a member inactive stops new loans",
                () -> svc.issueBook(bookId(svc, "Godan"), tester.id()), "inactive");
        svc.saveMember(new MemberInput(tester.id(), "Test User", "9999900001", "", true));
        check("a member can be made active again", svc.getMember(tester.id()).active());

        section("Two people at the same time");
        Book lastCopy = svc.saveBook(book(0, "Last Copy", "Nobody", "", "Test", null, 1));
        List<Member> rivals = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rivals.add(svc.saveMember(new MemberInput(0, "Rival " + i, "98000000" + (10 + i), "", true)));
        }
        List<Throwable> results = runTogether(rivals.stream()
                .<Supplier<Object>>map(m -> () -> svc.issueBook(lastCopy.id(), m.id())).toList());
        long wins = results.stream().filter(r -> r == null).count();
        check("8 clerks grab the last copy at once: exactly 1 gets it", wins == 1);
        check("the other 7 are told no copy is available", results.stream().filter(r -> r != null)
                .allMatch(r -> r instanceof LibraryException && r.getMessage().toLowerCase().contains("no copy")));
        check("the book shows 0 available (never negative)", svc.getBook(lastCopy.id()).availableCopies() == 0);

        Member busy = svc.saveMember(new MemberInput(0, "Busy Reader", "9800000099", "", true));
        List<Supplier<Object>> grabs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Book b = svc.saveBook(book(0, "Stack " + i, "Pile", "", "Test", null, 5));
            grabs.add(() -> svc.issueBook(b.id(), busy.id()));
        }
        List<Throwable> limitResults = runTogether(grabs);
        check("one member asking for 5 books at once gets only 3", limitResults.stream().filter(r -> r == null).count() == 3);
        check("and has exactly 3 books out", svc.getMember(busy.id()).booksOut() == 3);
    }

    // ------------------------------------------------------------------- helpers

    /** Starts all tasks at the same moment; returns null for a task that worked, or its error. */
    private static List<Throwable> runTogether(List<Supplier<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Supplier<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.get();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private static BookInput book(int id, String title, String author, String isbn, String category, Integer year, int copies) {
        return new BookInput(id, title, author, isbn, category, year, copies);
    }

    private static int bookId(LibraryService svc, String title) {
        return svc.searchBooks(title, BookField.TITLE).stream().filter(b -> b.title().equals(title))
                .findFirst().orElseThrow().id();
    }

    private static List<String> titles(List<Book> books) {
        return books.stream().map(Book::title).toList();
    }

    private static List<String> names(List<Member> members) {
        return members.stream().map(Member::name).toList();
    }

    private static List<String> sorted(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }

    private static Clock at(LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(day.atTime(10, 0).atZone(zone).toInstant(), zone);
    }

    private static void section(String name) {
        System.out.println();
        System.out.println(name);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok    " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    /** Passes if the action is refused with a message that contains the given words. */
    private static void expectError(String name, Runnable action, String messageContains) {
        try {
            action.run();
            check(name + " (nothing was refused!)", false);
        } catch (LibraryException e) {
            boolean ok = e.getMessage().toLowerCase().contains(messageContains.toLowerCase());
            check(name + (ok ? "" : "  [wrong message: " + e.getMessage() + "]"), ok);
        } catch (RuntimeException e) {
            check(name + "  [unexpected " + e + "]", false);
        }
    }
}
