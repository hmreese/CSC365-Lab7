import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import java.util.Scanner;
import java.util.Map.Entry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

public class InnReservations {

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(System.getenv("IR_JDBC_URL"),
                System.getenv("IR_JDBC_USER"), System.getenv("IR_JDBC_PW"))) {
            Scanner s = new Scanner(System.in);
            int option = 100;

            while (option != 0) {
                System.out.print(
                        "\nHi! Options:\n1 - Rooms & Rates\n2 - Reservations\n3 - Reservation Change\n4 - Reservation Cancellation\n5 - Detailed Reservation Info\n6 - Revenue\n7 - Reset Database\n0 - Exit\n\nSelect Option: ");

                if (s.hasNextInt()) {
                    option = s.nextInt();
                    s.nextLine();
                } else {
                    System.out.println("\nOption should be an integer");
                    s.nextLine();
                    continue;
                }

                if (option == 1) {
                    FR1(conn);
                } else if (option == 2) {
                    FR2(conn, s);
                } else if (option == 3) {
                    FR3(conn, s);
                } else if (option == 4) {
                    FR4(conn, s);
                } else if (option == 5) {
                    FR5(conn, s);
                } else if (option == 6) {
                    FR6(conn);
                } else if (option == 7) {
                    start(conn); // Resets database to initial state
                } else if (option == 0) {
                    System.out.println("Bye!");
                    break;
                } else {
                    System.out.println("Invalid Option, Try Again!");
                }
            }

            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void start(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS echekhan.lab7_rooms (\nRoomCode char(5) PRIMARY KEY,\nRoomName varchar(30) NOT NULL,\nBeds int(11) NOT NULL,\nbedType varchar(8) NOT NULL,\nmaxOcc int(11) NOT NULL,\nbasePrice DECIMAL(6,2) NOT NULL,\ndecor varchar(20) NOT NULL,\nUNIQUE (RoomName)\n);");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS echekhan.lab7_reservations (\nCODE int(11) PRIMARY KEY,\nRoom char(5) NOT NULL,\nCheckIn date NOT NULL,\nCheckout date NOT NULL,\nRate DECIMAL(6,2) NOT NULL,\nLastName varchar(15) NOT NULL,\nFirstName varchar(15) NOT NULL,\nAdults int(11) NOT NULL,\nKids int(11) NOT NULL,\nFOREIGN KEY (Room) REFERENCES echekhan.lab7_rooms (RoomCode)\n);");

            stmt.execute("DELETE FROM echekhan.lab7_reservations;");
            stmt.execute("DELETE FROM echekhan.lab7_rooms;");

            stmt.execute("INSERT INTO echekhan.lab7_rooms SELECT * FROM INN.rooms;");

            stmt.execute(
                    "INSERT INTO echekhan.lab7_reservations SELECT CODE, Room,\nDATE_ADD(CheckIn, INTERVAL 134 MONTH),\nDATE_ADD(Checkout, INTERVAL 134 MONTH),\nRate, LastName, FirstName, Adults, Kids FROM INN.reservations;");
        }
    }

    private static void FR1(Connection conn) {
        System.out.println("Rooms & Rates\n");

        HashMap<String, ArrayList<String>> stay_length = new HashMap<String, ArrayList<String>>();

        String sql = "SELECT Room, Checkout, DATEDIFF(Checkout, CheckIn) AS length\n"
                + "FROM echekhan.lab7_reservations AS r1\n"
                + "WHERE Checkout = (\n"
                + "\tSELECT MAX(Checkout)\n"
                + "\tFROM echekhan.lab7_reservations AS r2\n"
                + "\tWHERE r1.Room = r2.Room AND Checkout <= DATE(NOW()));";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String room = rs.getString("Room");
                stay_length.put(room, new ArrayList<String>());
                int length = rs.getInt("length");
                stay_length.get(room).add(String.format("%d", length));
                LocalDate last_checkout = LocalDate.parse(rs.getString("Checkout"));
                stay_length.get(room).add(last_checkout.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        sql = "SELECT roomCode,\n"
                + "SUM(CASE\n"
                + "\tWHEN (CheckIn < DATE_SUB(CURDATE(), INTERVAL 180 DAY)) AND (Checkout <= DATE_SUB(CURDATE(), INTERVAL 180 DAY))\n"
                + "\t\tTHEN 0"
                + "\tWHEN (Checkin <=  DATE_SUB(CURDATE(), INTERVAL 180 DAY)) AND (Checkout >= CURDATE())\n"
                + "\t\tTHEN 180\n"
                + "\tWHEN (Checkin < DATE_SUB(CURDATE(), INTERVAL 180 DAY)) AND (Checkout < CURDATE())\n"
                + "\t\tTHEN DATEDIFF(Checkout, DATE_SUB(CURDATE(), INTERVAL 180 DAY))\n"
                + "\tWHEN (CheckIn > DATE_SUB(CURDATE(), INTERVAL 180 DAY)) AND (Checkout > CURDATE())\n"
                + "\t\tTHEN DATEDIFF(CURDATE(), CheckIn)\n"
                + "\tWHEN (Checkin > DATE_SUB(CURDATE(), INTERVAL 180 DAY)) AND (Checkout < CURDATE())\n"
                + "\t\tTHEN DATEDIFF(Checkout, CheckIn)\n"
                + "END) AS DaysOccupied,\n"
                + "MAX(Checkout) AS LastDay\n"
                + "FROM echekhan.lab7_rooms\n"
                + "JOIN echekhan.lab7_reservations ON RoomCode = Room\n"
                + "GROUP BY roomCode\n"
                + "ORDER BY DaysOccupied DESC;";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("Room | Popularity Score | Next Check-In | Most Recent Stay Length | Latest Checkout");
            while (rs.next()) {
                String room = rs.getString("RoomCode");
                Double pop = rs.getDouble("DaysOccupied");
                pop = pop / 180;
                LocalDate next_avail = LocalDate.parse(rs.getString("LastDay")).plusDays(1);
                String length = stay_length.get(room).get(0);
                String date = stay_length.get(room).get(1);
                System.out.format("%-3s  | %,.2f             | %-10s    |   %-3s                   | %-10s\n", room,
                        pop,
                        next_avail, length, date);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        System.out.print("\n");
    }

    private static void FR2(Connection conn, Scanner s) {
        class roomSuggestion {
            String roomCode;
            String roomName;
            String bedType;
            int basePrice;
            LocalDate beginDate;
            LocalDate endDate;

            public roomSuggestion(String roomCode, String roomName, String bedType,
                    int basePrice, LocalDate beginDate, LocalDate endDate) {
                this.roomCode = roomCode;
                this.roomName = roomName;
                this.bedType = bedType;
                this.basePrice = basePrice;
                this.beginDate = beginDate;
                this.endDate = endDate;
            }
        } // Suggestions: same decor, any bed type, 15 days extra in both directions
        roomSuggestion[] suggestions = null;
        String firstName, lastName, roomCode, bedType;
        LocalDate beginDate, endDate;
        int numChildren, numAdults, lastCode = 0, selection;
        boolean exit = false;

        System.out.println("\n\nReservations\n");

        try {
            System.out.print("First Name: ");
            firstName = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.print("Last Name: ");
            lastName = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.print("Room code (Any for no preference): ");
            roomCode = s.nextLine().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            System.out.print("Bed type (Any for no preference): ");
            bedType = s.nextLine().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            bedType = bedType.substring(0, 1).toUpperCase() + bedType.substring(1);
            System.out.print("Begin date: ");
            beginDate = LocalDate.parse(s.nextLine());
            System.out.print("End date: ");
            endDate = LocalDate.parse(s.nextLine());
            System.out.print("Number of children: ");
            numChildren = s.nextInt();
            s.nextLine();
            System.out.print("Number of adults: ");
            numAdults = s.nextInt();
            s.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid date");
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            String sql = "SELECT *, COUNT(*) OVER() AS RowCount, (SELECT MAX(CODE) FROM echekhan.lab7_reservations) AS LastCode"
                    + "\nFROM echekhan.lab7_rooms\nWHERE "
                    + (roomCode.equals("ANY") ? "" : ("RoomCode = '" + roomCode + "'\nAND "))
                    + (bedType.equals("Any") ? "" : ("bedType = '" + bedType + "'\nAND "))
                    + "maxOcc >= " + (numChildren + numAdults) + "\nAND "
                    + "RoomCode NOT IN (SELECT DISTINCT Room FROM echekhan.lab7_reservations"
                    + "\n\tWHERE '" + beginDate.toString() + "' < CheckOut"
                    + "\n\tAND '" + endDate.toString() + "' > CheckIn)";
            ResultSet rs = stmt.executeQuery(sql);

            for (int i = 0; rs.next(); i++) {
                if (suggestions == null) {
                    suggestions = new roomSuggestion[rs.getInt("RowCount")];
                    lastCode = rs.getInt("LastCode");
                }

                suggestions[i] = new roomSuggestion(rs.getString("RoomCode"), rs.getString("RoomName"),
                        rs.getString("bedType"), rs.getInt("basePrice"), beginDate, endDate);
            }

            if (suggestions == null) {
                System.out.println("\nNo matches for your search. Here are some recommendations.");
                Hashtable<String, List<LocalDate[]>> blacklist = new Hashtable<String, List<LocalDate[]>>();
                Hashtable<String, roomSuggestion> roomInfo = new Hashtable<String, roomSuggestion>();
                sql = "SELECT *, COUNT(*) OVER() AS RowCount, MAX(CODE) OVER() AS LastCode "
                        + "FROM echekhan.lab7_rooms JOIN echekhan.lab7_reservations ON RoomCode = Room\nWHERE "
                        + "maxOcc >= " + (numChildren + numAdults) + "\nAND "
                        + "'" + beginDate.plusDays(-30).toString() + "' < CheckOut\nAND "
                        + "'" + endDate.plusDays(30).toString() + "' > CheckIn";
                rs = stmt.executeQuery(sql);

                while (rs.next()) {
                    lastCode = rs.getInt("LastCode");
                    if (blacklist.get(rs.getString("Room")) == null)
                        blacklist.put(rs.getString("Room"), new ArrayList<LocalDate[]>());
                    if (roomInfo.get(rs.getString("Room")) == null)
                        roomInfo.put(rs.getString("Room"), new roomSuggestion(rs.getString("Room"),
                                rs.getString("RoomName"), rs.getString("bedType"), rs.getInt("basePrice"), beginDate,
                                endDate));

                    blacklist.get(rs.getString("Room")).add(new LocalDate[] { LocalDate.parse(rs.getString("CheckIn")),
                            LocalDate.parse(rs.getString("CheckOut")) });
                }

                int suggestionIndex = 0;

                while (suggestionIndex < 5 && blacklist.size() > 0) {
                    if (suggestions == null) {
                        suggestions = new roomSuggestion[5];
                    }

                    Entry<String, List<LocalDate[]>> nextRoom = blacklist.entrySet().iterator().next();
                    int roomLimit = 2;

                    for (int j = 0; j < nextRoom.getValue().size() - 1; j++) {
                        if (ChronoUnit.DAYS.between(nextRoom.getValue().get(j)[1],
                                nextRoom.getValue().get(j + 1)[0]) >= ChronoUnit.DAYS.between(beginDate, endDate)) {
                            suggestions[suggestionIndex] = new roomSuggestion(nextRoom.getKey(), roomInfo.get(
                                    nextRoom.getKey()).roomName, roomInfo.get(nextRoom.getKey()).bedType,
                                    roomInfo.get(nextRoom.getKey()).basePrice,
                                    nextRoom.getValue().get(j)[1], nextRoom.getValue().get(j)[1]
                                            .plusDays(ChronoUnit.DAYS.between(beginDate, endDate)));

                            if (++suggestionIndex == 5)
                                break;
                            if (--roomLimit == 0)
                                break;
                        }
                    }

                    blacklist.remove(nextRoom.getKey());
                }

                if (suggestions == null) {
                    System.out.println("\nNo suitable rooms available.");
                    return;
                }
            }

            System.out.println();
            for (int i = 0; i < suggestions.length; i++) {
                if (suggestions[i] != null)
                    System.out.println(String.format("%d - %s,  Begin: %s,  End: %s",
                            i + 1, suggestions[i].roomName, suggestions[i].beginDate, suggestions[i].endDate));
            }
            System.out.println("-1 to return to main menu");

            while (!exit) {
                System.out.print("Select option: ");

                if (s.hasNextInt()) {
                    selection = s.nextInt();
                    s.nextLine();
                } else {
                    System.out.println("\nReservation code should be an integer");
                    s.nextLine();
                    continue;
                }

                if (selection == -1) {
                    exit = true;
                    continue;
                } else if (selection > suggestions.length || selection < 1) {
                    System.out.println("\nInvalid option");
                    continue;
                } else {
                    roomSuggestion room = suggestions[selection - 1];
                    int totalCost = 0, duration = 0;

                    for (LocalDate day = room.beginDate; day.compareTo(room.endDate) < 0; day = day.plusDays(1)) {
                        totalCost += room.basePrice * (day.getDayOfWeek() == DayOfWeek.SATURDAY
                                || day.getDayOfWeek() == DayOfWeek.SUNDAY ? 1.1 : 1);
                        duration += 1;
                    }

                    System.out.println(String.format(
                            "\nReservation Details\nReservation Code: %d\nName: %s %s\nRoom: %s - %s, "
                                    + " Bed: %s\nDates: %s  -  %s\nOccupants: %d Adults,  %d Children\nTotal Cost: %d",
                            lastCode + 1, firstName, lastName, room.roomCode, room.roomName, room.bedType,
                            room.beginDate.toString(), room.endDate.toString(), numAdults, numChildren, totalCost));
                    System.out.print("\nConfirm selection (y/n): ");

                    if (s.nextLine().toLowerCase().charAt(0) == 'y') {
                        sql = String.format("INSERT INTO echekhan.lab7_reservations "
                                + "(CODE, Room, CheckIn, CheckOut, Rate, LastName, FirstName, Adults, Kids)"
                                + "VALUES (%d, '%s', '%s', '%s', %.2f, '%s', '%s', %d, %d)", lastCode + 1,
                                room.roomCode, room.beginDate.toString(), room.endDate.toString(),
                                (float) totalCost / duration, lastName, firstName, numAdults, numChildren);
                        stmt.executeUpdate(sql);
                    }

                    exit = true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void FR3(Connection conn, Scanner s) {

        System.out.println("\nReservation Change");
        boolean exit = false;
        int code = -1;
        ArrayList<String> fields;

        while (!exit) {
            System.out.print("\nPlease enter your reservation code: ");

            if (s.hasNextInt()) {
                code = s.nextInt();
                s.nextLine();
            } else {
                System.out.println("\nReservation code should be an integer");
                continue;
            }

            // Get Room From Reservation Code
            String sql = "SELECT * FROM echekhan.lab7_reservations WHERE CODE = ?;";

            String room = null;
            int k = 0;
            int a = 0;
            String cin = null;
            String cout = null;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, code);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    room = rs.getString("Room");
                    k = rs.getInt("Kids");
                    a = rs.getInt("Adults");
                    cin = rs.getString("CheckIn");
                    cout = rs.getString("Checkout");

                } else {
                    System.out.println("No existing reservation under that code.");
                    continue;
                }

            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            System.out.println("Entering edit mode... If field does not require change, enter 'x'");
            System.out.println("First Name: ");
            String firstname = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.println("Last Name: ");
            String lastname = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.println("Check In (YYYY-MM-DD): ");
            String checkin = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.println("Check Out (YYYY-MM-DD): ");
            String checkout = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.println("Number of Children: ");
            String kids = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
            System.out.println("Number of Adults: ");
            String adults = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");

            // Check the Dates
            sql = "SELECT * FROM echekhan.lab7_reservations WHERE Room = ? AND Checkout > ? AND CheckIn < ? AND CODE != ?;";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, room);

                if (!checkin.equals("x")) {
                    pstmt.setString(2, checkin);
                } else {
                    pstmt.setString(2, cin);
                }
                if (!checkout.equals("x")) {
                    pstmt.setString(3, checkout);
                } else {
                    pstmt.setString(3, cout);
                }

                pstmt.setInt(4, code);

                ResultSet rs = pstmt.executeQuery();
                if (rs.next() != false) {
                    System.out.println("\nSorry, those dates are unavailable, try again!");
                    continue;
                }

            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            // Final Update Statement
            sql = "UPDATE echekhan.lab7_reservations\nSET ";

            boolean before = false;
            if (!firstname.equals("x")) {
                sql = sql + "Firstname = ?";
                before = true;
            }
            if (!lastname.equals("x")) {
                if (before = true){
                    sql = sql + ", Lastname = ?";
                }else{
                    sql = sql + "Lastname = ?";
                    before = true;
                }
            }
            if (!checkin.equals("x")) {
                if (before = true){
                    sql = sql + ", CheckIn = ?";
                }else{
                    sql = sql + "CheckIn = ?";
                    before = true;
                }
            }
            if (!checkout.equals("x")) {
                if (before = true){
                    sql = sql + ", Checkout = ?";
                }else{
                    sql = sql + "Checkout = ?";
                    before = true;
                }
            }
            if (!kids.equals("x")) {
                if (before = true){
                    sql = sql + ", Children = ?";
                }else{
                    sql = sql + "Children = ?";
                    before = true;
                }
            }
            if (!adults.equals("x")) {
                if (before = true){
                    sql = sql + ", Adults = ?";
                }else{
                    sql = sql + "Adults = ?";
                    before = true;
                }
            }

            sql = sql + "\nWHERE CODE = ?;";

            if (before){
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    int i = 1;

                    if (!firstname.equals("x")) {
                        pstmt.setString(i, firstname);
                        i++;
                    }
                    if (!lastname.equals("x")) {
                        pstmt.setString(i, lastname);
                        i++;
                    }
                    if (!checkin.equals("x")) {
                        pstmt.setString(i, checkin);
                        i++;
                    }
                    if (!checkout.equals("x")) {
                        pstmt.setString(i, checkout);
                        i++;
                    }
                    if (!kids.equals("x")) {
                        pstmt.setString(i, kids);
                        i++;
                    }
                    if (!adults.equals("x")) {
                        pstmt.setString(i, adults);
                        i++;
                    }

                    pstmt.setInt(i, code);

                    System.out.println(sql);

                    int rows = pstmt.executeUpdate();
                    if (rows == 0) {
                        System.out.println("Reservation Change Failed");
                        continue;
                    } else {
                        System.out.format("\nReservation Change Successful!\n");
                        System.out.format("\tFirst Name -> %s\n", firstname);
                        System.out.format("\tLast Name  -> %s\n", lastname);
                        System.out.format("\tCheck In   -> %s\n", checkin);
                        System.out.format("\tCheck Out  -> %s\n", checkout);
                        System.out.format("\tChildren   -> %s\n", kids);
                        System.out.format("\tAdults     -> %s\n", adults);
                        exit = true;
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    return;
                }
            }else{
                System.out.println("No changes made!");
            }
        }

    }

    private static void FR4(Connection conn, Scanner s) {
        boolean exit = false;
        int reservationCode;
        char confirm;

        System.out.println("\n\nReservation Cancellation");

        while (!exit) {
            System.out.print("\nEnter a reservation code (-1 to return to main menu): ");

            if (s.hasNextInt()) {
                reservationCode = s.nextInt();
                s.nextLine();
            } else {
                System.out.println("\nReservation code should be an integer");
                s.nextLine();
                continue;
            }

            if (reservationCode == -1) {
                exit = true;
                continue;
            }

            System.out.print(String.format("Confirm cancellation of reservation %d (y/n): ", reservationCode));
            confirm = s.nextLine().toLowerCase().charAt(0);

            if (confirm != 'y')
                continue;

            try (Statement stmt = conn.createStatement()) {
                String sql = String.format("DELETE FROM echekhan.lab7_reservations WHERE CODE = %d",
                        reservationCode);

                if (1 == stmt.executeUpdate(sql)) {
                    System.out.println("\nCancellation successful");
                    exit = true;
                } else
                    System.out.println("\nInvalid reservation code");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void FR5(Connection conn, Scanner s) {
        System.out.println("Detailed Reservation Info");
        System.out.println("\nEnter Any Search Parameters (x for skip):");

        System.out.println("First Name (add % for partials): ");
        String firstname = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
        System.out.println("Last Name (add % for partials): ");
        String lastname = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
        System.out.println("Check In (YYYY-MM-DD): ");
        String checkin = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
        System.out.println("Check Out (YYYY-MM-DD): ");
        String checkout = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
        System.out.println("Room Code: ");
        String room = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");
        System.out.println("Reservation Code: ");
        String res = s.nextLine().replaceAll("[^a-zA-Z0-9]", "");

        String sql = "SELECT * "
                + "\nFROM echekhan.lab7_reservations JOIN echekhan.lab7_rooms ON RoomCode = Room";

        boolean before = false;
        if (!firstname.equals("x")) {
            sql = sql + "\nWHERE Firstname = ?";
            before = true;
        }
        if (!lastname.equals("x")) {
            if (!before) {
                sql = sql + "\nWHERE Lastname = ?";
                before = true;
            } else {
                sql = sql + " AND Lastname = ?";
            }
        }
        if (!checkin.equals("x")) {
            if (!before) {
                sql = sql + "\nWHERE CheckIn = ?";
                before = true;
            } else {
                sql = sql + " AND CheckIn = ?";
            }
        }
        if (!checkout.equals("x")) {
            if (!before) {
                sql = sql + "\nWHERE Checkout = ?";
                before = true;
            } else {
                sql = sql + " AND Checkout = ?";
            }
        }
        if (!room.equals("x")) {
            if (!before) {
                sql = sql + "\nWHERE Room = ?";
                before = true;
            } else {
                sql = sql + " AND Room = ?";
            }
        }
        if (!res.equals("x")) {
            if (!before) {
                sql = sql + "\nWHERE CODE = ?";
                before = true;
            } else {
                sql = sql + " AND CODE = ?";
            }
        }

        sql = sql + ";";

        System.out.println(
                "Code  | Room |       Room Name           |  Check In  | Check Out  |  Rate  |    Last Name    |   First Name    | Adults | Kids\n");
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int i = 1;
            if (!firstname.equals("x")) {
                pstmt.setString(i, firstname);
                i++;
            }
            if (!lastname.equals("x")) {
                pstmt.setString(i, lastname);
                i++;
            }
            if (!checkin.equals("x")) {
                pstmt.setString(i, checkin);
                i++;
            }
            if (!checkout.equals("x")) {
                pstmt.setString(i, checkout);
                i++;
            }
            if (!room.equals("x")) {
                pstmt.setString(i, room);
                i++;
            }
            if (!res.equals("x")) {
                pstmt.setString(i, res);
                i++;
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String Code = rs.getString("CODE");
                String Room = rs.getString("Room");
                String Name = rs.getString("RoomName");
                String Checkin = rs.getString("CheckIn");
                String Checkout = rs.getString("Checkout");
                String Rate = rs.getString("Rate");
                String Lastname = rs.getString("LastName");
                String Firstname = rs.getString("FirstName");
                String Adults = rs.getString("Adults");
                String Kids = rs.getString("Kids");
                System.out.format("%-5s | %-3s  | %-25s | %-10s | %-10s | %-6s | %-15s | %-15s | %-2s     | %-2s\n",
                        Code, Room, Name, Checkin, Checkout,
                        Rate, Lastname, Firstname, Adults, Kids);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
    }

    private static void FR6(Connection conn) {
        Hashtable<String, float[]> rooms = new Hashtable<String, float[]>();
        float[] monthTotals = new float[13];

        System.out.println("\n\nRevenue\n");

        try (Statement stmt = conn.createStatement()) {
            String sql = "select * from echekhan.lab7_reservations "
                    + "WHERE YEAR(CheckIn) <= YEAR(CURDATE()) AND YEAR(CheckOut) >= YEAR(CURDATE())";
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String room = rs.getString("Room");
                float rate = rs.getFloat("Rate");
                LocalDate checkIn = rs.getDate("CheckIn").toLocalDate();
                LocalDate checkOut = rs.getDate("CheckOut").toLocalDate();

                if (rooms.get(room) == null)
                    rooms.put(room, new float[13]);

                for (LocalDate day = checkIn; day.compareTo(checkOut) < 0; day = day.plusDays(1)) {
                    if (day.getYear() == LocalDate.now().getYear()) {
                        rooms.get(room)[day.getMonthValue() - 1] += rate;
                        rooms.get(room)[12] += rate;
                        monthTotals[day.getMonthValue() - 1] += rate;
                        monthTotals[12] += rate;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.print("Room  |    Jan    |    Feb    |    Mar    |    Apr    |    May    |    Jun    |    Jul    |" +
                "    Aug    |    Sep    |    Oct    |    Nov    |    Dec    |   Total   |");
        for (Entry<String, float[]> room : rooms.entrySet()) {
            System.out.print(String.format("\n%-6s| ", room.getKey()));

            for (float total : room.getValue())
                System.out.print(String.format("%-10.0f| ", total));
        }
        System.out.print(String.format("\n%-6s| ", "Total"));
        for (float total : monthTotals)
            System.out.print(String.format("%-10.0f| ", total));
        System.out.println();
    }
}