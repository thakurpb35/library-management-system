package library.service;

import library.config.AppConfig;
import library.dao.BookDao;
import library.dao.IssueDao;
import library.dao.MemberDao;
import library.dao.StatsDao;
import library.db.Database;
import library.model.Book;
import library.model.BookField;
import library.model.BookInput;
import library.model.Issue;
import library.model.IssueFilter;
import library.model.IssueStatus;
import library.model.IssueView;
import library.model.Member;
import library.model.MemberInput;
import library.model.Stats;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * All the library rules live here: the screens only call these methods, and the
 * methods only talk to the database through the DAO classes.
 */
public final class LibraryService {

    private static final Pattern ISBN = Pattern.compile("[0-9Xx-]{10,17}");
    private static final Pattern PHONE = Pattern.compile("\\d{10}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final Database db;
    private final AppConfig config;
    private final Clock clock;

    public LibraryService(Database db, AppConfig config, Clock clock) {
        this.db = db;
        this.config = config;
        this.clock = clock;
    }

    public AppConfig config() {
        return config;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------ books

    public List<Book> searchBooks(String text, BookField field) {
        return db.read(c -> BookDao.search(c, text, field));
    }

    public Book getBook(int id) {
        return db.read(c -> BookDao.find(c, id));
    }

    public List<String> categories() {
        return db.read(BookDao::categories);
    }

    /** Adds a book (id 0) or changes an existing one. */
    public Book saveBook(BookInput in) {
        String title = clean(in.title());
        String author = clean(in.author());
        String category = clean(in.category());
        String isbn = clean(in.isbn()).replace(" ", "").toUpperCase();
        if (title.isEmpty()) {
            throw new LibraryException("Please enter the book's title.");
        }
        if (title.length() > 200) {
            throw new LibraryException("The title is too long (200 letters at most).");
        }
        if (author.isEmpty()) {
            throw new LibraryException("Please enter the author's name.");
        }
        if (author.length() > 120) {
            throw new LibraryException("The author's name is too long (120 letters at most).");
        }
        if (category.isEmpty()) {
            throw new LibraryException("Please choose or type a category.");
        }
        if (category.length() > 60) {
            throw new LibraryException("The category name is too long (60 letters at most).");
        }
        if (!isbn.isEmpty() && !ISBN.matcher(isbn).matches()) {
            throw new LibraryException("The ISBN should have 10 or 13 digits (hyphens are fine).");
        }
        Integer year = in.year();
        if (year != null && (year < 1000 || year > today().getYear() + 1)) {
            throw new LibraryException("Please enter a valid publication year, or leave it empty.");
        }
        if (in.totalCopies() < 1) {
            throw new LibraryException("A book needs at least 1 copy.");
        }
        String isbnValue = isbn.isEmpty() ? null : isbn;
        return db.transaction(c -> {
            if (in.id() == 0) {
                int id = BookDao.insert(c, title, author, isbnValue, category, year, in.totalCopies());
                return BookDao.find(c, id);
            }
            BookDao.Lock lock = BookDao.lock(c, in.id());
            if (lock == null) {
                throw new LibraryException("That book no longer exists.");
            }
            int out = IssueDao.countOpenForBook(c, in.id());
            if (in.totalCopies() < out) {
                throw new LibraryException(out + " cop" + (out == 1 ? "y is" : "ies are")
                        + " out on loan right now, so the total cannot be less than " + out + ".");
            }
            BookDao.update(c, in.id(), title, author, isbnValue, category, year, in.totalCopies());
            return BookDao.find(c, in.id());
        });
    }

    public void deleteBook(int id) {
        db.transaction(c -> {
            BookDao.Lock lock = BookDao.lock(c, id);
            if (lock == null) {
                throw new LibraryException("That book no longer exists.");
            }
            if (IssueDao.countAllForBook(c, id) > 0) {
                throw new LibraryException("\"" + lock.title() + "\" has issue records, so it cannot be deleted.");
            }
            BookDao.delete(c, id);
            return null;
        });
    }

    // ---------------------------------------------------------------- members

    public List<Member> searchMembers(String text) {
        return db.read(c -> MemberDao.search(c, text));
    }

    public Member getMember(int id) {
        return db.read(c -> MemberDao.find(c, id));
    }

    /** Adds a member (id 0) or changes an existing one. */
    public Member saveMember(MemberInput in) {
        String name = clean(in.name());
        String phone = clean(in.phone()).replace(" ", "");
        String email = clean(in.email());
        if (name.isEmpty()) {
            throw new LibraryException("Please enter the member's name.");
        }
        if (name.length() > 100) {
            throw new LibraryException("The name is too long (100 letters at most).");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw new LibraryException("The phone number must have exactly 10 digits.");
        }
        if (!email.isEmpty() && (email.length() > 120 || !EMAIL.matcher(email).matches())) {
            throw new LibraryException("That email address does not look right.");
        }
        String emailValue = email.isEmpty() ? null : email;
        return db.transaction(c -> {
            if (in.id() == 0) {
                int id = MemberDao.insert(c, name, phone, emailValue, today(), in.active());
                return MemberDao.find(c, id);
            }
            if (MemberDao.lock(c, in.id()) == null) {
                throw new LibraryException("That member no longer exists.");
            }
            MemberDao.update(c, in.id(), name, phone, emailValue, in.active());
            return MemberDao.find(c, in.id());
        });
    }

    public void deleteMember(int id) {
        db.transaction(c -> {
            MemberDao.Lock lock = MemberDao.lock(c, id);
            if (lock == null) {
                throw new LibraryException("That member no longer exists.");
            }
            if (IssueDao.countAllForMember(c, id) > 0) {
                throw new LibraryException(lock.name() + " has issue records, so the member cannot be deleted."
                        + " Mark the member as inactive instead.");
            }
            MemberDao.delete(c, id);
            return null;
        });
    }

    // ------------------------------------------------------- issue and return

    /**
     * Gives one copy of a book to a member. The book row is locked first, so even if two
     * computers try to issue the last copy at the same moment, only one of them gets it.
     */
    public IssueView issueBook(int bookId, int memberId) {
        LocalDate issuedOn = today();
        LocalDate dueOn = issuedOn.plusDays(config.loanDays());
        return db.transaction(c -> {
            BookDao.Lock book = BookDao.lock(c, bookId);
            if (book == null) {
                throw new LibraryException("There is no book with ID " + bookId + ". Check the Books tab.");
            }
            MemberDao.Lock member = MemberDao.lock(c, memberId);
            if (member == null) {
                throw new LibraryException("There is no member with ID " + memberId + ". Check the Members tab.");
            }
            if (!member.active()) {
                throw new LibraryException(member.name() + " is marked inactive, so books cannot be issued.");
            }
            int limit = config.maxBooksPerMember();
            if (IssueDao.countOpenForMember(c, memberId) >= limit) {
                throw new LibraryException(member.name() + " already has " + limit
                        + " books out, which is the limit. A book must be returned first.");
            }
            if (IssueDao.hasOpen(c, bookId, memberId)) {
                throw new LibraryException(member.name() + " already has a copy of \"" + book.title() + "\".");
            }
            if (IssueDao.countOpenForBook(c, bookId) >= book.totalCopies()) {
                throw new LibraryException("No copy of \"" + book.title() + "\" is available right now"
                        + " (all " + book.totalCopies() + " on loan).");
            }
            int id = IssueDao.insert(c, bookId, memberId, issuedOn, dueOn);
            return view(IssueDao.find(c, id));
        });
    }

    /** What returning this record today would look like (days late and the fine), without changing anything. */
    public IssueView previewReturn(int issueId) {
        return db.read(c -> {
            Issue issue = IssueDao.find(c, issueId);
            if (issue == null) {
                throw new LibraryException("That issue record no longer exists.");
            }
            return view(issue);
        });
    }

    /** Takes a book back today and records the fine for any late days. */
    public IssueView returnBook(int issueId) {
        LocalDate returnedOn = today();
        return db.transaction(c -> {
            IssueDao.Lock lock = IssueDao.lock(c, issueId);
            if (lock == null) {
                throw new LibraryException("That issue record no longer exists.");
            }
            if (lock.returnedOn() != null) {
                throw new LibraryException("That book was already returned on " + lock.returnedOn() + ".");
            }
            BigDecimal fine = FineCalculator.fine(lock.dueOn(), returnedOn, config.finePerDay());
            IssueDao.markReturned(c, issueId, returnedOn, fine);
            return view(IssueDao.find(c, issueId));
        });
    }

    public List<IssueView> listIssues(IssueFilter filter, Integer memberId) {
        LocalDate today = today();
        return db.read(c -> IssueDao.list(c, filter, memberId, today).stream().map(this::view).toList());
    }

    public Stats stats() {
        LocalDate today = today();
        return db.read(c -> StatsDao.load(c, today));
    }

    // ---------------------------------------------------------------- helpers

    /** Works out the status, days late and fine of a record as of today. */
    private IssueView view(Issue issue) {
        if (issue.isReturned()) {
            return new IssueView(issue, IssueStatus.RETURNED,
                    FineCalculator.daysLate(issue.dueOn(), issue.returnedOn()), issue.fineCharged());
        }
        int late = FineCalculator.daysLate(issue.dueOn(), today());
        return new IssueView(issue, late > 0 ? IssueStatus.OVERDUE : IssueStatus.ON_LOAN, late,
                FineCalculator.fine(late, config.finePerDay()));
    }

    /** Trims the text and squeezes repeated spaces; null becomes an empty string. */
    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
