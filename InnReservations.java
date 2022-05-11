import java.sql.*;

import java.util.Map;
import java.util.Scanner;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class InnReservations {

	public static void main(String[] args)
	{
		start();
		Scanner s = new Scanner(System.in);
		System.out.println("Hi! Options:\n1-Rooms&Rates\n2-Reservations\n3-ReservationChange\n4-ReservationCancellation\n5-DetailedReservationInfo\n6-Revenue");
		int option = 100;

		while(option != 0)
		{
			option = s.nextInt();
			s.nextLine();

			if (option == 1)
			{
				FR1();
			}
			else if (option == 2)
			{
				FR2();
			}
			else if (option == 3)
			{
				FR3();
			}
			else if (option == 4)
			{
				FR4();
			}
			else if (option == 5)
			{
				FR5();
			}
			else if (option == 6)
			{
				FR6();
			}
			else
			{
				System.out.println("Invalid Option, Try Again!");
			}
		}

		s.close();
	}

	private static void start()
	{
		System.out.println("starting");

		try {
			try{
				Class.forName("com.mysql.jdbc.Driver");
				System.out.println("MySQL JDBC Driver loaded");
			} catch (ClassNotFoundException ex) {
				System.err.println("Unable to load JDBC Driver");
				System.exit(-1);
			} 
			DriverManager.setLoginTimeout(15);
			Connection conn = DriverManager.getConnection(System.getenv("IR_JDBC_URL"), System.getenv("IR_JDBC_USER"), System.getenv("IR_JDBC_PW"));
			
			System.out.println("WHAT");
			try (Statement stmt = conn.createStatement())
			{
				stmt.execute("CREATE TABLE IF NOT EXISTS lab7_rooms (\nRoomCode char(5) PRIMARY KEY,\nRoomName varchar(30) NOT NULL,\nBeds int(11) NOT NULL,\nbedType varchar(8) NOT NULL,\nmaxOcc int(11) NOT NULL,\nbasePrice DECIMAL(6,2) NOT NULL,\ndecor varchar(20) NOT NULL,\nUNIQUE (RoomName)\n);");

				stmt.execute("CREATE TABLE IF NOT EXISTS lab7_reservations (\nCODE int(11) PRIMARY KEY,\nRoom char(5) NOT NULL,\nCheckIn date NOT NULL,\nCheckout date NOT NULL,\nRate DECIMAL(6,2) NOT NULL,\nLastName varchar(15) NOT NULL,\nFirstName varchar(15) NOT NULL,\nAdults int(11) NOT NULL,\nKids int(11) NOT NULL,\nFOREIGN KEY (Room) REFERENCES lab7_rooms (RoomCode)\n);");

				stmt.execute("INSERT INTO lab7_rooms SELECT * FROM INN.rooms;");
				stmt.execute("INSERT INTO lab7_reservations SELECT CODE, Room,\nDATE_ADD(CheckIn, INTERVAL 134 MONTH),\nDATE_ADD(Checkout, INTERVAL 134 MONTH),\nRate, LastName, FirstName, Adults, Kids FROM INN.reservations;");
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void FR1()
	{
		System.out.println("Rooms & Rates");
		Connection conn;
		// Step 1: Establish connection to RDBMS
		try
		{
			conn = DriverManager.getConnection(System.getenv("IR_JDBC_URL"),
							   System.getenv("IR_JDBC_USER"),
							   System.getenv("IR_JDBC_PW"));
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return;
		}
	
		System.out.println("connection successful");
	   
		// Step 2: Construct SQL statement
		String sql = "SELECT * FROM ir_rooms";

		// Step 4: Send SQL statement to DBMS
		try (Statement stmt = conn.createStatement();
		 ResultSet rs = stmt.executeQuery(sql)) {

			// Step 5: Receive results
			while (rs.next()) {
				String room = rs.getString("RoomName");
				System.out.format("%s", room);
			}
	    }
		catch (SQLException e)
		{
			e.printStackTrace();
			return;
		}
	}
	

	private static void FR2()
	{
		System.out.println("Reservations");
	}

	private static void FR3()
	{
		System.out.println("Reservation Change");
	}
	
	private static void FR4()
	{
		System.out.println("Reservation Cancellation");
	}

	private static void FR5()
	{
		System.out.println("Detailed Reservation Info");
	}
	
	private static void FR6()
	{
		System.out.println("Revenue");
	}

}