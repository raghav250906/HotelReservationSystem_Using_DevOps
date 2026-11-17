
import java.sql.*;
import java.util.ArrayList;

public class HotelManager {

    private ArrayList<Room> rooms;
    private ArrayList<Reservation> reservations;

    public HotelManager() {
        rooms = new ArrayList<>();
        reservations = new ArrayList<>();

        loadData();

        if (rooms.isEmpty()) {
            addDefaultRooms();
        }
    }

    // ================= DEFAULT ROOMS =================

    private void addDefaultRooms() {

        addRoom("101", "Standard", 2000);
        addRoom("102", "Standard", 2000);

        addRoom("201", "Deluxe", 3500);
        addRoom("202", "Deluxe", 3500);

        addRoom("301", "Suite", 5500);
        addRoom("302", "Suite", 5500);
    }

    // ================= LOAD DATA =================

    private void loadData() {

        rooms.clear();
        reservations.clear();

        try (Connection con = DBConnection.getConnection()) {

            // Load rooms
            String roomSQL = "SELECT * FROM rooms";

            try (PreparedStatement ps =
                         con.prepareStatement(roomSQL);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    Room room = new Room(
                            rs.getString("room_number"),
                            rs.getString("category"),
                            rs.getDouble("price_per_night")
                    );

                    room.setAvailable(
                            rs.getBoolean("available")
                    );

                    rooms.add(room);
                }
            }

            // Load reservations
            String reservationSQL =
                    "SELECT * FROM reservations";

            try (PreparedStatement ps =
                         con.prepareStatement(reservationSQL);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    Reservation reservation =
                            new Reservation(
                                    rs.getString("booking_id"),
                                    rs.getString("guest_name"),
                                    rs.getString("contact"),
                                    rs.getString("room_number"),
                                    rs.getString("room_category"),
                                    rs.getInt("nights"),
                                    rs.getDouble("total_amount"),
                                    rs.getString("payment_method"),
                                    rs.getString("payment_status")
                            );

                    reservations.add(reservation);
                }
            }

            System.out.println("Data loaded from MySQL.");

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Unable to load hotel data from MySQL.", e
            );
        }
    }

    // ================= GET ROOMS =================

    public ArrayList<Room> getRooms() {
        return rooms;
    }

    // ================= GET RESERVATIONS =================

    public ArrayList<Reservation> getReservations() {
        return reservations;
    }

    // ================= CHECK ROOM NUMBER =================

    public boolean roomNumberExists(String roomNumber) {

        return findRoom(roomNumber) != null;
    }

    // ================= ADD ROOM =================

    public void addRoom(
            String roomNumber,
            String category,
            double price) {

        if (roomNumberExists(roomNumber)) {
            throw new IllegalArgumentException(
                    "Room number already exists."
            );
        }

        String sql =
                "INSERT INTO rooms " +
                "(room_number, category, price_per_night, available) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, roomNumber);
            ps.setString(2, category);
            ps.setDouble(3, price);
            ps.setBoolean(4, true);

            ps.executeUpdate();

            rooms.add(new Room(roomNumber, category, price));

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Unable to add room.", e
            );
        }
    }

    // ================= SEARCH ROOMS =================

    public ArrayList<Room> searchAvailableRooms(
            String category) {

        ArrayList<Room> result = new ArrayList<>();

        for (Room room : rooms) {

            if (room.isAvailable() &&
                    (category.equalsIgnoreCase("All") ||
                     room.getCategory().equalsIgnoreCase(category))) {

                result.add(room);
            }
        }

        return result;
    }

    // ================= FIND ROOM =================

    public Room findRoom(String roomNumber) {

        for (Room room : rooms) {

            if (room.getRoomNumber()
                    .equalsIgnoreCase(roomNumber)) {

                return room;
            }
        }

        return null;
    }

    // ================= CREATE RESERVATION =================

    public Reservation createReservation(
            String guestName,
            String contact,
            Room room,
            int nights,
            String paymentMethod) {

        if (room == null || nights <= 0) {
            throw new IllegalArgumentException(
                    "Invalid room or number of nights."
            );
        }

        String bookingId = null;
        Reservation reservation = null;

        String roomSQL =
                "SELECT category, price_per_night, available " +
                "FROM rooms WHERE room_number = ? FOR UPDATE";

        String insertSQL =
                "INSERT INTO reservations " +
                "(booking_id, guest_name, contact, room_number, " +
                "room_category, nights, total_amount, " +
                "payment_method, payment_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateSQL =
                "UPDATE rooms SET available = FALSE " +
                "WHERE room_number = ?";

        try (Connection con = DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                String category;
                double price;

                // Lock and verify selected room
                try (PreparedStatement ps =
                             con.prepareStatement(roomSQL)) {

                    ps.setString(1, room.getRoomNumber());

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException("Room not found.");
                        }

                        if (!rs.getBoolean("available")) {
                            throw new SQLException(
                                    "Room is already booked."
                            );
                        }

                        category = rs.getString("category");
                        price = rs.getDouble("price_per_night");
                    }
                }

                double total = price * nights;

                bookingId = generateUniqueBookingId(con);

                reservation = new Reservation(
                        bookingId,
                        guestName,
                        contact,
                        room.getRoomNumber(),
                        category,
                        nights,
                        total,
                        paymentMethod,
                        "Paid"
                );

                // Insert reservation
                try (PreparedStatement ps =
                             con.prepareStatement(insertSQL)) {

                    ps.setString(1, bookingId);
                    ps.setString(2, guestName);
                    ps.setString(3, contact);
                    ps.setString(4, room.getRoomNumber());
                    ps.setString(5, category);
                    ps.setInt(6, nights);
                    ps.setDouble(7, total);
                    ps.setString(8, paymentMethod);
                    ps.setString(9, "Paid");

                    ps.executeUpdate();
                }

                // Mark room unavailable
                try (PreparedStatement ps =
                             con.prepareStatement(updateSQL)) {

                    ps.setString(1, room.getRoomNumber());
                    ps.executeUpdate();
                }

                con.commit();

                room.setAvailable(false);
                reservations.add(reservation);

                return reservation;

            } catch (Exception e) {

                con.rollback();

                throw e;
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to create reservation.", e
            );
        }
    }

    // ================= BOOKING ID =================

    private String generateUniqueBookingId(Connection con)
            throws SQLException {

        int number = 1;

        String sql =
                "SELECT COUNT(*) FROM reservations " +
                "WHERE booking_id = ?";

        while (true) {

            String id = String.format("BK%04d", number);

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setString(1, id);

                try (ResultSet rs = ps.executeQuery()) {

                    rs.next();

                    if (rs.getInt(1) == 0) {
                        return id;
                    }
                }
            }

            number++;
        }
    }

    // ================= CANCEL RESERVATION =================

    public boolean cancelReservation(String bookingId) {

        String roomNumber = null;

        try (Connection con = DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                String findSQL =
                        "SELECT room_number FROM reservations " +
                        "WHERE booking_id = ? FOR UPDATE";

                try (PreparedStatement ps =
                             con.prepareStatement(findSQL)) {

                    ps.setString(1, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            con.rollback();
                            return false;
                        }

                        roomNumber = rs.getString("room_number");
                    }
                }

                String deleteSQL =
                        "DELETE FROM reservations WHERE booking_id = ?";

                try (PreparedStatement ps =
                             con.prepareStatement(deleteSQL)) {

                    ps.setString(1, bookingId);
                    ps.executeUpdate();
                }

                String updateSQL =
                        "UPDATE rooms SET available = TRUE " +
                        "WHERE room_number = ?";

                try (PreparedStatement ps =
                             con.prepareStatement(updateSQL)) {

                    ps.setString(1, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();

                Room room = findRoom(roomNumber);

                if (room != null) {
                    room.setAvailable(true);
                }

                reservations.removeIf(
                        r -> r.getBookingId()
                                .equalsIgnoreCase(bookingId)
                );

                return true;

            } catch (Exception e) {

                con.rollback();
                throw e;
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to cancel reservation.", e
            );
        }
    }

    // ================= FIND RESERVATION =================

    public Reservation findReservation(String bookingId) {

        for (Reservation reservation : reservations) {

            if (reservation.getBookingId()
                    .equalsIgnoreCase(bookingId)) {

                return reservation;
            }
        }

        return null;
    }
}
