package library.model;

/** What the Add/Edit member form sends to the service. {@code id == 0} means a new member. */
public record MemberInput(int id, String name, String phone, String email, boolean active) {
}
