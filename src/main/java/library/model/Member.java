package library.model;

import java.time.LocalDate;

public record Member(int id, String name, String phone, String email, LocalDate joinedOn,
                     boolean active, int booksOut) {
}
