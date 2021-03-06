package libraryadminterminal;

import ejb.session.stateless.BookEntityControllerRemote;
import ejb.session.stateless.FineEntityControllerRemote;
import ejb.session.stateless.LendingEntityControllerRemote;
import ejb.session.stateless.MemberEntityControllerRemote;
import ejb.session.stateless.PaymentEntityControllerRemote;
import ejb.session.stateless.ReservationEntityControllerRemote;
import entity.BookEntity;
import entity.FineEntity;
import entity.LendingEntity;
import entity.MemberEntity;
import entity.ReservationEntity;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import util.exception.BookNotFoundException;
import util.exception.FineNotFoundException;
import util.exception.MemberNotFoundException;
import util.exception.ReservationNotFoundException;


public class LibraryOperation {
    private FineEntityControllerRemote fineEntityControllerRemote;
    private ReservationEntityControllerRemote reservationEntityControllerRemote;
    private BookEntityControllerRemote bookEntityControllerRemote;
    private PaymentEntityControllerRemote paymentEntityControllerRemote;
    private MemberEntityControllerRemote memberEntityControllerRemote;
    private LendingEntityControllerRemote lendingEntityControllerRemote;
    
    public LibraryOperation() {
    }

    public LibraryOperation(FineEntityControllerRemote fineEntityControllerRemote, ReservationEntityControllerRemote reservationEntityControllerRemote, BookEntityControllerRemote bookEntityControllerRemote, PaymentEntityControllerRemote paymentEntityControllerRemote, MemberEntityControllerRemote memberEntityControllerRemote, LendingEntityControllerRemote lendingEntityControllerRemote) {
        this.fineEntityControllerRemote = fineEntityControllerRemote;
        this.reservationEntityControllerRemote = reservationEntityControllerRemote;
        this.bookEntityControllerRemote = bookEntityControllerRemote;
        this.paymentEntityControllerRemote = paymentEntityControllerRemote;
        this.memberEntityControllerRemote = memberEntityControllerRemote;
        this.lendingEntityControllerRemote = lendingEntityControllerRemote;
    }

   
    public void menuLibrary() {
       
        Scanner scanner = new Scanner(System.in);
        Integer response = 0;
        
        while (true) {
            System.out.println("\n");
            System.out.println("*** ILS System :: Library Operation ***\n");
            System.out.println("1: Lend Book");
            System.out.println("2: View Lent Books");
            System.out.println("3: Return Book");
            System.out.println("4: Extend Book");
            System.out.println("5: Pay Fines");
            System.out.println("6: Manage Reservations");
            System.out.println("7: Back\n");
            response = 0;
            
            while (response < 1 || response > 7) {
                System.out.print("> ");

                response = scanner.nextInt();

                switch (response) {
                    case 1:
                        lendBook();
                        break;
                    case 2:
                        viewLentBooks();
                        break;
                    case 3:
                        returnBook();
                        break;
                
                    case 4:
                        extendBook();
                        break;
                
                    case 5:
                        payFines();
                        break;                
                
                    case 6:
                        manageReservations();
                        break;
                    case 7:
                        return;
                    default:
                        System.out.println("Invalid option, please try again!\n");
                        break;
                }
            }
             
            if (response == 7) {
                return;
            }
        }
    }
    
    
    private void lendBook() {
        Scanner scanner = new Scanner(System.in);
        LendingEntity newLendingEntity = new LendingEntity();
        MemberEntity thisMember = new MemberEntity();
        BookEntity thisBook = new BookEntity();
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: Lend Book ***\n");   
        System.out.print("Enter Member Identity Number> ");
        String ic = scanner.nextLine().trim();
        
        try {
           thisMember = memberEntityControllerRemote.retrieveMemberByIc(ic);
        } catch (MemberNotFoundException ex) {
            System.out.println("An error has occurred: " + ex.getMessage() + "\n");
            return;
        }
        
        /*** Reject Lend (Quit Method): Outstanding Fines. ***/
        if (!(fineEntityControllerRemote.retrieveBorrowerFines(ic)).isEmpty()) {
            System.out.println("Member restricted from borrowing due to outstanding fines.\n");
            return;
        }
        
        System.out.print("Enter Book ID> ");
        Long bookId = scanner.nextLong();
        
        try {
            thisBook = bookEntityControllerRemote.retrieveBookByBookId(bookId);
            
            /*** Case: Book has Reservations & Book is Available After Recent Return. ***/
            if (!(reservationEntityControllerRemote.retrieveBookReservations(bookId)).isEmpty() && thisBook.getAvailable() == 1) {
                List<ReservationEntity> listOfReservations = reservationEntityControllerRemote.retrieveBookReservations(bookId);
                ReservationEntity firstReservation = new ReservationEntity();
                
                if (!listOfReservations.isEmpty() && listOfReservations.size() > 0) {
                    firstReservation = listOfReservations.get(0);
                }
                
                /*** Reject Borrowing (Quit Method): First in Reservation Line for Book is not Current Member. ***/
                if  (!(firstReservation.getMember().getIdentityNumber()).equals(thisMember.getIdentityNumber())) {
                    System.out.println("Book has already been reserved by a member.\n");
                    return; 
                }
                
                /*** Accept Borrowing: First in Reservation Line for Book is Current Member. ***/
                if  (firstReservation.getMember().getIdentityNumber().equals(thisMember.getIdentityNumber())) {
                    
                    /*** Delete Reservation and Continue Flow. ***/
                    try {
                    reservationEntityControllerRemote.deleteReservation(firstReservation.getReservationId());
                    } catch (ReservationNotFoundException ex) {
                        System.out.println("An error has occurred: " + ex.getMessage() + "\n");
                        return;    
                    }
                }  
            } /*** Case: Book has No Reservations & Book is Not Available. ***/
            else if (reservationEntityControllerRemote.retrieveBookReservations(bookId).isEmpty() && thisBook.getAvailable() == 0) {
                System.out.println("Book is not available for lending.\n");
                return;
            } /*** Case: Book has Reservations & Book is Not Available. ***/
            else if (!(reservationEntityControllerRemote.retrieveBookReservations(bookId).isEmpty()) && thisBook.getAvailable() == 0) {
                System.out.println("Book has already been reserved by a member.\n");
                return;
            }
            
            /*** Reject Lend (Quit Method): Book Unavailable. ***/
            if (thisBook.getAvailable() < 1) {
                System.out.println("Book is not available for lending.\n");
                return;
            }
            
            if (thisMember.getBookBorrowed() < 3) {    
                
                newLendingEntity.setBook(thisBook);
                newLendingEntity.setMember(thisMember);

                Calendar cal = Calendar.getInstance();
                Date today = cal.getTime();
                cal.add(Calendar.DAY_OF_YEAR, 14);
                Date due = cal.getTime();
                        
                newLendingEntity.setLendDate(today);
                newLendingEntity.setDueDate(due);
                newLendingEntity.setFine(null);

                lendingEntityControllerRemote.createNewLending(newLendingEntity);

                thisBook.setAvailable(thisBook.getAvailable()-1);
                thisMember.setBookBorrowed(thisMember.getBookBorrowed()+1);
                bookEntityControllerRemote.updateBook(thisBook);
                memberEntityControllerRemote.updateMember(thisMember);
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                System.out.println("Successfully lent book to member. Due Date: " + sdf.format(due) +".\n");  
            } else {
                /*** Reject Lend (Quit Method): Exceed Borrow Count. ***/
                System.out.println("Member has exceeded the borrowing limit.\n");
            }
        } catch (BookNotFoundException ex) {
            System.out.println("An error has occurred: " + ex.getMessage() + "\n");
            return;
        }
    }
    
    private void viewLentBooks() 
    {
        Scanner scanner = new Scanner(System.in);
        MemberEntity thisMember = new MemberEntity();
        List<LendingEntity> listOfCurrentLendings = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: View Lent Books ***\n");
        System.out.print("Enter Member Identity Number> ");
        
        try {
            String ic = scanner.nextLine().trim();
            thisMember = memberEntityControllerRemote.retrieveMemberByIc(ic);
            
            System.out.println("\n");
            System.out.println("Currently Lent Books: ");
            
            listOfCurrentLendings = lendingEntityControllerRemote.retrieveCurrentLendings(ic);
            
            System.out.printf("%-12s%-50s%-25s\n", "Id", "Title", "Due Date");
            if (!listOfCurrentLendings.isEmpty()) {
                for (LendingEntity lendings : listOfCurrentLendings) {
                    System.out.printf("%-12s%-50s%-25s\n", lendings.getBook().getBookId().toString(), lendings.getBook().getTitle(), sdf.format(lendings.getDueDate()));
                }
            } else {
                System.out.println("Member has not borrowed any books.");
                return;
            }
        } catch (MemberNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
    }
    
    private void returnBook() {
        Scanner scanner = new Scanner(System.in);
        MemberEntity thisMember = new MemberEntity();
        List<LendingEntity> listOfCurrentLendings = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: View Lent Books ***\n");
        System.out.print("Enter Member Identity Number> ");
        String ic = scanner.nextLine().trim();
        
        try {    
            thisMember = memberEntityControllerRemote.retrieveMemberByIc(ic);
            
            System.out.println("\n");
            System.out.println("Currently Lent Books: ");
            
            listOfCurrentLendings = lendingEntityControllerRemote.retrieveCurrentLendings(ic);
            
            System.out.printf("%-12s%-50s%-25s\n", "Id", "Title", "Due Date");
            if (!listOfCurrentLendings.isEmpty()) {
                for (LendingEntity lendings : listOfCurrentLendings) {
                    System.out.printf("%-12s%-50s%-25s\n", lendings.getBook().getBookId().toString(), lendings.getBook().getTitle(), sdf.format(lendings.getDueDate()));
                }
            } else {
                System.out.println("Member did not borrow any books. \n");
                return;
            }
        } catch (MemberNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
        
        BookEntity thisBook = new BookEntity();
        LendingEntity thisLending = new LendingEntity();
        
        System.out.println("\n");
        System.out.print("Enter Book to Return> ");
        Long bookId = scanner.nextLong();
        
        try {
            thisBook = bookEntityControllerRemote.retrieveBookByBookId(bookId);   
        } catch (BookNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
         
        Boolean containsBook = false;
        
        for (LendingEntity lending : listOfCurrentLendings) {
            if (!lending.getBook().getBookId().equals(bookId)) {
                containsBook =  false;
            } else {
                thisLending = lending;
                containsBook = true;
                break;
            }
        }
        
        if (!containsBook) {
            System.out.println("Book is not in current lendings.");
            return;
        }
        
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        thisLending.setReturnDate(today);
        
        thisBook.setAvailable(thisBook.getAvailable()+1);
        thisMember.setBookBorrowed(thisMember.getBookBorrowed()-1);
        lendingEntityControllerRemote.updateLending(thisLending);
        bookEntityControllerRemote.updateBook(thisBook);
        memberEntityControllerRemote.updateMember(thisMember);
        
        System.out.println("Book successfully returned. \n");

        if (today.compareTo(thisLending.getDueDate()) < 0) {
            return;
        }
        
        /*** Create Fine: If Today > Due Date. ***/
        FineEntity newFineEntity = new FineEntity();
        
        TimeUnit timeUnit = TimeUnit.DAYS;
        long millis = today.getTime() - (thisLending.getDueDate()).getTime();
        long days = timeUnit.convert(millis,TimeUnit.MILLISECONDS);
        
        newFineEntity.setAmount(BigDecimal.valueOf(days));
        newFineEntity.setLending(thisLending);
        fineEntityControllerRemote.createNewFine(newFineEntity);
    }
    
    private void extendBook() {
    
        Scanner scanner = new Scanner(System.in);
        MemberEntity thisMember = new MemberEntity();
        List<LendingEntity> listOfCurrentLendings = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: Extend Book ***\n");
        System.out.print("Enter Member Identity Number> ");
        String ic = scanner.nextLine().trim();
        
        try {    
            thisMember = memberEntityControllerRemote.retrieveMemberByIc(ic);
            
            System.out.println("\n");
            System.out.println("Currently Lent Books: ");
            
            listOfCurrentLendings = lendingEntityControllerRemote.retrieveCurrentLendings(ic);
            
            System.out.printf("%-12s%-50s%-25s\n", "Id", "Title", "Due Date");
            if (!listOfCurrentLendings.isEmpty()) {
                for (LendingEntity lendings : listOfCurrentLendings) {
                    System.out.printf("%-12s%-50s%-25s\n", lendings.getBook().getBookId().toString(), lendings.getBook().getTitle(), sdf.format(lendings.getDueDate()));
                }
            } else {
                System.out.println("Member did not borrow any books. \n");
                return;
            }
        } catch (MemberNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
        
        BookEntity thisBook = new BookEntity();
        LendingEntity thisLending = new LendingEntity();
        
        System.out.println("\n");
        System.out.print("Enter Book to Extend> ");
        Long bookId = scanner.nextLong();
        
        try {
            thisBook = bookEntityControllerRemote.retrieveBookByBookId(bookId);   
        } catch (BookNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
             
        Boolean containsBook = false;
        
        for (LendingEntity lending : listOfCurrentLendings) {
            if (!lending.getBook().getBookId().equals(bookId)) {
                containsBook =  false;
            } else {
                thisLending = lending;
                containsBook = true;
                break;
            }
        }
        
        if (!containsBook) {
            System.out.println("Book is not in current lendings.");
            return;
        }
        
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        
        /*** Reject Extend (Quit Method): Book is already overdue. ***/
        if (thisLending.getDueDate().before(today)){
            System.out.println("Unable to extend due to overdue loan.");
            return;
        }   
        
        /*** Reject Extend (Quit Method): Member has outstanding fines. ***/
        if (!(fineEntityControllerRemote.retrieveBorrowerFines(ic)).isEmpty())
        { 
            System.out.println("Unable to extend due to outstanding fines.");
            return;
        }
        
        /*** Reject Extend (Quit Method): Book is already reserved by a member. ***/
        if (!(reservationEntityControllerRemote.retrieveBookReservations(bookId)).isEmpty()) 
        {
            System.out.println("Unable to extend as book has already been reserved by a member.\n");
            return;
        } 
        
        Long due = thisLending.getDueDate().getTime();
        due+= 1209600000; 
        Date newDueDate = new Date(due);
        thisLending.setDueDate(newDueDate);
        
        lendingEntityControllerRemote.updateLending(thisLending);

        System.out.println("Book successfully extended. New due date: " + sdf.format(newDueDate));
    }
    
    private void payFines() {
        Scanner scanner = new Scanner(System.in);
        List<FineEntity> outstandingFines = new ArrayList<>();
        MemberEntity thisMember = new MemberEntity();
 
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: Pay Fines ***\n");
        System.out.print("Enter Member Identity Number> ");
        String ic = scanner.nextLine().trim();
        
        try {    
            thisMember = memberEntityControllerRemote.retrieveMemberByIc(ic);
            
            System.out.println("\n");
            System.out.println("Unpaid Fines for Member: ");
            
            outstandingFines = fineEntityControllerRemote.retrieveOutstandingFines(ic);
            
            System.out.printf("%-8s%-20s\n", "Id", "Amount");
            if (!outstandingFines.isEmpty()) {
                for (FineEntity fines : outstandingFines) {
                    System.out.printf("%-8s%-20s\n", fines.getFineId(), NumberFormat.getCurrencyInstance().format(fines.getAmount()));
                }
            } else {
                System.out.println("Member does not have outstanding fines. \n");
                return;
            }
        } 
        catch (MemberNotFoundException | FineNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
        
        System.out.print("\n");
        System.out.print("Enter Fine to Settle> ");
        Long fineId = scanner.nextLong();
        
        FineEntity thisFine = new FineEntity();
        
        try {
            thisFine = fineEntityControllerRemote.retrieveFineByFineId(fineId);
        } catch(FineNotFoundException ex) {
            System.out.println("An error has occurred: " + ex.getMessage() + "\n");
            return; 
        }
       
        Boolean containsFine = false;
        
        for (FineEntity fine : outstandingFines) {
            if (!fine.getFineId().equals(fineId)) {
                containsFine =  false;
            } else {
                thisFine = fine;
                containsFine = true;
                break;
            }
        }
        
        if (!containsFine) {
            System.out.println("Fine does not exist.");
            return;
        }
        
        System.out.print("Select Payment Method (1: Cash, 2: Card)> ");
        Integer response = scanner.nextInt();
        
        if (response == 1 || response == 2) {
            try {
                fineEntityControllerRemote.deleteFine(fineId);
                System.out.println("Fine successfully paid.");
            } catch (FineNotFoundException ex) {
                System.out.println("An error has occurred: " + ex.getMessage() + "\n");
                return;
            }
        } else {
            System.out.println("Invalid option, please try again!\n");
        }
    }
    
    private void manageReservations() {
        
        Scanner scanner = new Scanner(System.in);
        Integer response = 0;
        
        while (true) {
            System.out.println("\n");
            System.out.println("*** ILS :: Library Operation :: Manage Reservations ***\n");
            System.out.println("1: View Reservations for Book");
            System.out.println("2: Delete Reservation");
            System.out.println("3: Back \n");
            response = 0;
            
            while (response < 1 || response > 3) {
                System.out.print("> ");

                response = scanner.nextInt();

                switch (response) {
                    case 1:
                        viewReservationsForBooks();
                        break;
                    case 2:
                        deleteReservation();
                        break;
                    case 3:
                        return;
                    default:
                        System.out.println("Invalid option, please try again!\n");
                        break;
                }
            }
            if (response == 3) {
                return;
            }
        }
    }
    
    private void viewReservationsForBooks(){
        
        Scanner scanner = new Scanner(System.in);
        BookEntity thisBook = new BookEntity();
        List<ReservationEntity> listOfReservations = new ArrayList<>();
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: View Reservations for Book ***\n");
        System.out.print("Enter Book ISBN> ");
        
        try {
            String isbn = scanner.nextLine().trim();
            thisBook = bookEntityControllerRemote.retrieveBookByIsbn(isbn);
            
            listOfReservations = reservationEntityControllerRemote.retrieveBookReservations(thisBook.getBookId());
            
            System.out.println("\n");
            System.out.println("Currently Reserved Books: ");
            
            listOfReservations = reservationEntityControllerRemote.retrieveBookReservations(thisBook.getBookId());
            
            System.out.printf("%-12s%-50s%-40s%-15s%-15s\n", "ID", "Book Title", "Member Identification Number", "Queue Number", "Fulfilled");
            if (!listOfReservations.isEmpty()) {
                for (ReservationEntity reservations : listOfReservations) {
                    System.out.printf("%-12s%-50s%-40s%-15s%-15s\n", reservations.getReservationId(), reservations.getBook().getTitle(), reservations.getMember().getIdentityNumber(), reservations.getQueueNumber(), reservations.getFulfilled());
                }
            } else {
                System.out.println("The book has no reservations.");
                return;
            }
        } catch (BookNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }     
    }
    
    private void deleteReservation() {
        Scanner scanner = new Scanner(System.in);
        BookEntity thisBook = new BookEntity();
        List<ReservationEntity> listOfReservations = new ArrayList<>();
        
        System.out.println("\n");
        System.out.println("*** ILS :: Library Operation :: Delete Reservation ***\n");
        System.out.print("Enter Book ISBN> ");
        
        try {
            String isbn = scanner.nextLine().trim();
            thisBook = bookEntityControllerRemote.retrieveBookByIsbn(isbn);
            
            listOfReservations = reservationEntityControllerRemote.retrieveBookReservations(thisBook.getBookId());
            
            System.out.println("\n");
            System.out.println("Currently Reserved Books: ");
            
            listOfReservations = reservationEntityControllerRemote.retrieveBookReservations(thisBook.getBookId());
            
            System.out.printf("%-12s%-30s%-40s%-15s%-15s\n", "Reservation ID", "Book Title", "Member Identification Number", "Queue Number", "Fulfilled");
            if (!listOfReservations.isEmpty()) {
                for (ReservationEntity reservations : listOfReservations) {
                    System.out.printf("%-12s%-30s%-40s%-15s%-15s\n", reservations.getReservationId(), reservations.getBook().getTitle(), reservations.getMember().getIdentityNumber(), reservations.getQueueNumber(), reservations.getFulfilled());
                }
            } else {
                System.out.println("The book has no reservations.");
                return;
            }
        } catch (BookNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return; 
        }
        
        
        System.out.print("Enter Reservation ID> ");    
        Long reservationId = scanner.nextLong();
        
        ReservationEntity thisReservation = new ReservationEntity();       
        
        try {
            thisReservation = reservationEntityControllerRemote.retrieveReservationByReservationId(reservationId);
        } catch (ReservationNotFoundException ex) {
            System.out.println("An error has occurred: " + ex.getMessage() + "\n");
            return; 
        }
        
        Boolean containsReservation = false;
        
        for (ReservationEntity reservation : listOfReservations) {
            if (!reservation.getReservationId().equals(reservationId)) {
                containsReservation =  false;
            } else {
                thisReservation = reservation;
                containsReservation = true;
                break;
            }
        }
        
        if (!containsReservation) {
            System.out.println("Reservation does not exist.");
            return;
        }
        
        try {
            reservationEntityControllerRemote.deleteReservation(reservationId);
            System.out.println("Reservation successfully deleted.");
        } catch (ReservationNotFoundException ex) {
           System.out.println("An error has occurred: " + ex.getMessage() + "\n");
           return;
        }  
    }   
}