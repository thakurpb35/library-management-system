package library.ui;

import library.model.Member;
import library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;

/** Search, add, edit and delete members. */
final class MembersPanel extends JPanel implements Refreshable {

    private final MainFrame owner;
    private final LibraryService service;
    private final JTextField search = new JTextField(26);
    private final JLabel count = Ui.muted(" ");
    private final ListTableModel<Member> model;
    private final JTable table;

    MembersPanel(MainFrame owner) {
        super(new BorderLayout(0, 10));
        this.owner = owner;
        this.service = owner.service();
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        search.setName("members.search");
        JButton clear = new JButton("Clear");
        clear.setName("members.clear");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(new JLabel("Search"));
        bar.add(search);
        bar.add(clear);
        bar.add(Ui.muted("Type a member ID, a name, a phone number or an email"));
        add(bar, BorderLayout.NORTH);

        model = new ListTableModel<Member>()
                .col("ID", Integer.class, Member::id)
                .col("Name", String.class, Member::name)
                .col("Phone", String.class, Member::phone)
                .col("Email", String.class, Member::email)
                .col("Joined", LocalDate.class, Member::joinedOn)
                .col("Status", String.class, m -> m.active() ? "Active" : "Inactive")
                .col("Books out", Integer.class, Member::booksOut);
        table = Ui.table(model, service.config().currency());
        table.setName("members.table");
        int[] widths = {50, 200, 120, 240, 110, 80, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        add(Ui.scroll(table), BorderLayout.CENTER);

        JButton add = new JButton("Add member");
        add.setName("members.add");
        JButton edit = new JButton("Edit selected");
        edit.setName("members.edit");
        JButton delete = new JButton("Delete selected");
        delete.setName("members.delete");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.WEST);
        south.add(count, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        Ui.onChange(search, () -> Ui.guard(this, this::refresh));
        clear.addActionListener(e -> search.setText(""));
        add.addActionListener(e -> addMember());
        edit.addActionListener(e -> editSelected());
        delete.addActionListener(e -> deleteSelected());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });
    }

    @Override
    public void refresh() {
        List<Member> members = service.searchMembers(search.getText());
        model.setRows(members);
        count.setText(members.size() + (members.size() == 1 ? " member" : " members"));
    }

    private void addMember() {
        if (MemberDialog.open(owner, null)) {
            Ui.guard(this, this::refresh);
            owner.status("Member added.");
        }
    }

    private void editSelected() {
        Member member = Ui.selected(table, model);
        if (member == null) {
            Dialogs.error(owner, "Select a member in the table first.");
            return;
        }
        if (MemberDialog.open(owner, member)) {
            Ui.guard(this, this::refresh);
            owner.status("Member " + member.name() + " updated.");
        }
    }

    private void deleteSelected() {
        Member member = Ui.selected(table, model);
        if (member == null) {
            Dialogs.error(owner, "Select a member in the table first.");
            return;
        }
        if (!Dialogs.confirm(owner, "Delete member", "Delete " + member.name() + "?\nThis cannot be undone.", "Delete")) {
            return;
        }
        Ui.guard(this, () -> {
            service.deleteMember(member.id());
            refresh();
            owner.status("Member " + member.name() + " deleted.");
        });
    }
}
