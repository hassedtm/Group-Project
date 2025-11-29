/* 
 Group Members:
 - Christian Caraballo, Hassid Trimarchi, Nikita Ryabtsev.
 Contributions:
 - Christian Caraballo: Drafting the initial code.
 - Hassid Trimarchi: (insert contributions here)
 - Nikita Ryabtsev: (insert contributions here)
 
 
*/

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LibraryBookManager extends Application {

    private TableView<Book> tableView;
    private TextField titleField;
    private ComboBox<Author> authorComboBox;
    private TextField yearField;
    private Button addButton, updateButton, deleteButton, refreshButton;
    private Label statusLabel;

    private final ObservableList<Book> books = FXCollections.observableArrayList();
    private final ObservableList<Author> authors = FXCollections.observableArrayList();

    private DatabaseManager dbManager;

    @Override
    public void start(Stage primaryStage) {
        try {
            dbManager = new DatabaseManager(
                    "jdbc:mysql://localhost:3306/librarydb",
                    "scott",
                    "tiger");
        } catch (SQLException e) {
            showFatalDatabaseError(e);
            return;
        }

        // Table setup
        tableView = new TableView<>();
        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(250);

        TableColumn<Book, String> authorCol = new TableColumn<>("Author Name");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("authorName"));
        authorCol.setPrefWidth(200);

        TableColumn<Book, Integer> yearCol = new TableColumn<>("Year Published");
        yearCol.setCellValueFactory(new PropertyValueFactory<>("yearPublished"));
        yearCol.setPrefWidth(120);

        tableView.getColumns().addAll(titleCol, authorCol, yearCol);
        tableView.setItems(books);
        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldSel, newSel) -> populateFieldsFromSelection(newSel));

        // Input fields
        titleField = new TextField();
        titleField.setPromptText("Title (max 50 chars)");

        authorComboBox = new ComboBox<>(authors);
        authorComboBox.setPromptText("Select author");

        yearField = new TextField();
        yearField.setPromptText("Year (YYYY)");

        addButton = new Button("Add");
        updateButton = new Button("Update");
        deleteButton = new Button("Delete");
        refreshButton = new Button("Refresh");

        HBox inputRow = new HBox(10,
                new VBox(new Label("Title"), titleField),
                new VBox(new Label("Author"), authorComboBox),
                new VBox(new Label("Year"), yearField));
        inputRow.setPadding(new Insets(10));

        HBox buttonsRow = new HBox(10, addButton, updateButton, deleteButton, refreshButton);
        buttonsRow.setPadding(new Insets(10));

        statusLabel = new Label("Ready.");
        statusLabel.setPadding(new Insets(8));

        VBox root = new VBox(10, tableView, inputRow, buttonsRow, statusLabel);
        root.setPadding(new Insets(10));

        // Actions
        addButton.setOnAction(e -> handleAdd());
        updateButton.setOnAction(e -> handleUpdate());
        deleteButton.setOnAction(e -> handleDelete());
        refreshButton.setOnAction(e -> loadAllData());

        // Initial load
        loadAllData();

        Scene scene = new Scene(root, 600, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Library Book Manager");
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            try {
                dbManager.close();
            } catch (SQLException ex) {
            }
            Platform.exit();
        });
    }

    private void loadAllData() {
        try {
            authors.setAll(dbManager.getAllAuthors());
            books.setAll(dbManager.getAllBooks());
            statusLabel.setText("Data loaded. Authors: " + authors.size() + ", Books: " + books.size());
        } catch (SQLException e) {
            statusLabel.setText("Error loading data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void populateFieldsFromSelection(Book b) {
        if (b == null) {
            titleField.clear();
            yearField.clear();
            authorComboBox.getSelectionModel().clearSelection();
            return;
        }
        titleField.setText(b.getTitle());
        yearField.setText(String.valueOf(b.getYearPublished()));
        authors.stream().filter(a -> a.getAuthorID() == b.getAuthorID())
                .findFirst().ifPresent(authorComboBox.getSelectionModel()::select);
    }

    private void handleAdd() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        Author selectedAuthor = authorComboBox.getValue();
        String yearText = yearField.getText() == null ? "" : yearField.getText().trim();

        if (!validateInputs(title, selectedAuthor, yearText))
            return;

        try {
            int year = Integer.parseInt(yearText);
            int newId = dbManager.addBook(title, selectedAuthor.getAuthorID(), year);
            loadAllData();
            statusLabel.setText("Added book (ID " + newId + ").");
            clearInputs();
        } catch (SQLException e) {
            statusLabel.setText("Add failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleUpdate() {
        Book selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a book to update.");
            return;
        }
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        Author selectedAuthor = authorComboBox.getValue();
        String yearText = yearField.getText() == null ? "" : yearField.getText().trim();

        if (!validateInputs(title, selectedAuthor, yearText))
            return;

        try {
            int year = Integer.parseInt(yearText);
            boolean ok = dbManager.updateBook(selected.getBookID(), title, selectedAuthor.getAuthorID(), year);
            if (ok) {
                loadAllData();
                statusLabel.setText("Updated book ID " + selected.getBookID());
            } else {
                statusLabel.setText("Update failed: book might not exist.");
            }
            clearInputs();
        } catch (SQLException e) {
            statusLabel.setText("Update failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDelete() {
        Book selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a book to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete book: " + selected.getTitle() + "?",
                ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.YES) {
            statusLabel.setText("Delete canceled.");
            return;
        }

        try {
            boolean ok = dbManager.deleteBook(selected.getBookID());
            if (ok) {
                loadAllData();
                statusLabel.setText("Deleted book ID " + selected.getBookID());
            } else {
                statusLabel.setText("Delete failed: book might not exist.");
            }
            clearInputs();
        } catch (SQLException e) {
            statusLabel.setText("Delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearInputs() {
        titleField.clear();
        yearField.clear();
        authorComboBox.getSelectionModel().clearSelection();
        tableView.getSelectionModel().clearSelection();
    }

    private boolean validateInputs(String title, Author author, String yearText) {
        if (title.isEmpty()) {
            statusLabel.setText("Title is required.");
            return false;
        }
        if (title.length() > 50) {
            statusLabel.setText("Title must be 50 characters or fewer.");
            return false;
        }
        if (author == null) {
            statusLabel.setText("Select an author.");
            return false;
        }
        if (yearText.isEmpty()) {
            statusLabel.setText("Year is required.");
            return false;
        }
        if (!yearText.matches("\\d{4}")) {
            statusLabel.setText("Year must be a 4-digit number (YYYY).");
            return false;
        }
        int year = Integer.parseInt(yearText);
        if (year < 1000 || year > 9999) {
            statusLabel.setText("Year must be a valid 4-digit year.");
            return false;
        }
        return true;
    }

    private void showFatalDatabaseError(SQLException e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to connect to the database:\n" + e.getMessage(),
                ButtonType.OK);
        alert.setHeaderText("Database connection failed");
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

/** DatabaseManager class */
class DatabaseManager {
    private final Connection conn;

    public DatabaseManager(String url, String user, String password) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
        conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(true);
    }

    public List<Author> getAllAuthors() throws SQLException {
        List<Author> list = new ArrayList<>();
        String sql = "SELECT AuthorID, Name FROM Authors ORDER BY Name";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Author(rs.getInt("AuthorID"), rs.getString("Name")));
            }
        }
        return list;
    }

    public List<Book> getAllBooks() throws SQLException {
        List<Book> list = new ArrayList<>();
        String sql = "SELECT b.BookID, b.Title, b.AuthorID, a.Name AS AuthorName, b.YearPublished " +
                "FROM Books b JOIN Authors a ON b.AuthorID = a.AuthorID ORDER BY b.Title";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Book(rs.getInt("BookID"), rs.getString("Title"), rs.getInt("AuthorID"),
                        rs.getString("AuthorName"), rs.getInt("YearPublished")));
            }
        }
        return list;
    }

    public int addBook(String title, int authorID, int yearPublished) throws SQLException {
        String sql = "INSERT INTO Books (Title, AuthorID, YearPublished) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setInt(2, authorID);
            ps.setInt(3, yearPublished);
            int affected = ps.executeUpdate();
            if (affected == 0)
                throw new SQLException("Insert failed, no rows affected.");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
            throw new SQLException("Insert failed, no ID obtained.");
        }
    }

    public boolean updateBook(int bookID, String title, int authorID, int yearPublished) throws SQLException {
        String sql = "UPDATE Books SET Title = ?, AuthorID = ?, YearPublished = ? WHERE BookID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, authorID);
            ps.setInt(3, yearPublished);
            ps.setInt(4, bookID);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteBook(int bookID) throws SQLException {
        String sql = "DELETE FROM Books WHERE BookID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookID);
            return ps.executeUpdate() > 0;
        }
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }
}

/** Book model class */
class Book {
    private final IntegerProperty bookID;
    private final StringProperty title;
    private final IntegerProperty authorID;
    private final StringProperty authorName;
    private final IntegerProperty yearPublished;

    public Book(int bookID, String title, int authorID, String authorName, int yearPublished) {
        this.bookID = new SimpleIntegerProperty(bookID);
        this.title = new SimpleStringProperty(title);
        this.authorID = new SimpleIntegerProperty(authorID);
        this.authorName = new SimpleStringProperty(authorName);
        this.yearPublished = new SimpleIntegerProperty(yearPublished);
    }

    public int getBookID() {
        return bookID.get();
    }

    public IntegerProperty bookIDProperty() {
        return bookID;
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String t) {
        this.title.set(t);
    }

    public int getAuthorID() {
        return authorID.get();
    }

    public IntegerProperty authorIDProperty() {
        return authorID;
    }

    public String getAuthorName() {
        return authorName.get();
    }

    public StringProperty authorNameProperty() {
        return authorName;
    }

    public int getYearPublished() {
        return yearPublished.get();
    }

    public IntegerProperty yearPublishedProperty() {
        return yearPublished;
    }

    public void setYearPublished(int y) {
        this.yearPublished.set(y);
    }

    @Override
    public String toString() {
        return getTitle();
    }
}

/** Author model class */
class Author {
    private final IntegerProperty authorID;
    private final StringProperty name;

    public Author(int authorID, String name) {
        this.authorID = new SimpleIntegerProperty(authorID);
        this.name = new SimpleStringProperty(name);
    }

    public int getAuthorID() {
        return authorID.get();
    }

    public IntegerProperty authorIDProperty() {
        return authorID;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
