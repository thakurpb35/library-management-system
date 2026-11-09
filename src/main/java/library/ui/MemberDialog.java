package library.ui;

import library.model.Member;
import library.model.MemberInput;
import library.service.LibraryService;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagLayout;
import java.awt.Window;

/** The Add member / Edit member form. */
final class MemberDialog extends FormDialog {

    private final LibraryService service;
    private final Member existing;
    private final JTextField name = new JTextField(26);
    private final JTextField phone = new JTextField(26);
    private final JTextField email = new JTextField(26);
    private final JCheckBox active = new JCheckBox("Active (allowed to borrow books)", true);

    private MemberDialog(Window owner, LibraryService service, Member existing) {
        super(owner, existing == null ? "Add a member" : "Edit member #" + existing.id());
        this.service = service;
        this.existing = existing;

        name.setName("member.name");
        phone.setName("member.phone");
        email.setName("member.email");
        active.setName("member.active");

        if (existing != null) {
            name.setText(existing.name());
            phone.setText(existing.phone());
            email.setText(existing.email() == null ? "" : existing.email());
            active.setSelected(existing.active());
        }

        JPanel form = new JPanel(new GridBagLayout());
        Ui.formRow(form, 0, "Name *", name);
        Ui.formRow(form, 1, "Phone *", phone);
        Ui.formRow(form, 2, "Email", email);
        Ui.formRow(form, 3, "", active);
        build(form, existing == null ? "Add member" : "Save changes");
    }

    /** Opens the form; returns true if a member was saved. */
    static boolean open(MainFrame owner, Member existing) {
        return new MemberDialog(owner, owner.service(), existing).showDialog();
    }

    @Override
    protected String save() {
        service.saveMember(new MemberInput(existing == null ? 0 : existing.id(), name.getText(),
                phone.getText(), email.getText(), active.isSelected()));
        return null;
    }
}
