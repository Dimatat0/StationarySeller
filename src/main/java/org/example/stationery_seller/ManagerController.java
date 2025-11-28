package org.example.stationery_seller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ManagerController {
    public TableView<Item> itemsTable;
    public @FXML TableColumn<Item, Integer> colId;
    public @FXML TableColumn<Item, String> colName;
    public @FXML TableColumn<Item, Double> colPrice;
    public @FXML TableColumn<Item, Integer> colQuantity;
    public @FXML TableColumn<Item, String> colCategory;
    public @FXML TableColumn<Item, String> colBarcode;

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<Category> categories = FXCollections.observableArrayList();

    private int UserId;
    private String fullName;
    private static final Logger log = Logger.getLogger(ManagerController.class.getName());
    ConnectionHelper helper = ConnectionHelper.getInstance();


    public void setUserId(int userId, String fullName){
        this.UserId = userId;
        this.fullName = fullName;
        loadItemsFromDB();
        loadCategoriesFromDB();
    }

    @FXML
    void onAccountExit() {
        try {
            Stage stage = (Stage) itemsTable.getScene().getWindow();

            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("Login.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1024, 768);

            stage.setTitle("Канцелярские товары");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Can't change scene", e);
        }
    }

    @FXML
    public void onExit() {
        Platform.exit();
    }

    @FXML
    public void onAbout() {
        showAlert(Alert.AlertType.INFORMATION, "О программе", "Приложение для продажи канцелярских товаров.\n Автор: Татарских Д.Н.");
    }



    @FXML
    private void loadItemsFromDB(){
        items.clear();
        try (Connection conn = helper.getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT i.ItemID, i.Name, i.Price, s.quantity, i.barcode, i.CategoryID ,c.name as CategoryName " +
                             "FROM Items i " +
                             "JOIN stock s ON i.ItemID = s.itemID " +
                             "JOIN item_category c ON i.CategoryID = c.CategoryID " +
                             "ORDER BY i.ItemID"
             );
             ResultSet result = statement.executeQuery()
        ) {
            while (result.next()){
                Item item = new Item(
                        result.getInt("ItemID"),
                        result.getString("Name"),
                        result.getDouble("Price"),
                        result.getInt("Quantity"),
                        result.getString("Barcode"),
                        result.getInt("CategoryID"),
                        result.getString("CategoryName")
                );
                items.add(item);
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database connection error", e);
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить товары из базы данных");
        }

        updateItemsUI();
    }

    private void loadCategoriesFromDB() {
        categories.clear();
        try (Connection conn = helper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT CategoryID, Name FROM item_category ORDER BY Name"
             );
             ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                categories.add(new Category(
                        rs.getInt("CategoryID"),
                        rs.getString("Name")
                ));
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load categories", e);
            showAlert(Alert.AlertType.ERROR,
                    "Ошибка",
                    "Не удалось загрузить категории из базы данных");
        }
    }

    @FXML
    private void refresh(){
        loadItemsFromDB();
        updateItemsUI();
    }


    private void updateItemsUI() {
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colId.setCellValueFactory(new PropertyValueFactory<>("ItemID"));
        colName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("itemPrice"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("itemQuantity"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("CategoryName"));
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("Barcode"));

        itemsTable.setItems(items);
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateItemInDB(Item item){
        try(Connection connection = helper.getConnection()){
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE Items SET Name = ?, Price = ? WHERE ItemID = ?"
            )){
                statement.setString(1, item.getItemName());
                statement.setDouble(2, item.getItemPrice());
                statement.setInt(3, item.getItemID());
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE stock SET quantity = ? WHERE ItemID = ?"
            )){
                statement.setInt(1, item.getItemQuantity());
                statement.setInt(2, item.getItemID());
                statement.executeUpdate();
            }
        }
        catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to update Item",e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Ошибка базы данных", "Не удалось сохранить изменения в базе данных. Проверьте подключение.");
        }
    }

    private void deleteItemFromDB(Item item) {
        try (Connection conn = helper.getConnection()) {

            try (PreparedStatement statement = conn.prepareStatement(
                    "DELETE FROM Items WHERE ItemID = ?"
            )) {
                statement.setInt(1, item.getItemID());
                statement.executeUpdate();
            }

        } catch (SQLException e) {
            if (e.getSQLState().equals("23503")) {
                showAlert(Alert.AlertType.WARNING, "Удаление невозможно",
                        "Не удалось удалить товар из базы данных, так как этот товар уже используются в чеках.");
                log.log(Level.INFO, "Failed to delete item because it's already in a some receipt");
            }
            else {
                showAlert(Alert.AlertType.ERROR,
                        "Ошибка",
                        "Не удалось удалить товар.");
                log.log(Level.SEVERE, "Failed to delete item", e);
            }

        }
    }

    private Item insertItemToDB(String name,
                                double price,
                                int quantity,
                                int categoryID,
                                String barcode,
                                String categoryName) {

        try (Connection conn = helper.getConnection()) {

            //начало транзакции
            conn.setAutoCommit(false);

            int newItemId;

            String insertItemSql = "INSERT INTO Items (Name, Price, CategoryID, barcode) " +
                    "VALUES (?, ?, ?, ?) RETURNING ItemID";
            try (PreparedStatement ps = conn.prepareStatement(insertItemSql)) {
                ps.setString(1, name);
                ps.setDouble(2, price);
                ps.setInt(3, categoryID);
                ps.setString(4, barcode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        newItemId = rs.getInt("ItemID");
                    } else {
                        conn.rollback();
                        throw new SQLException("Не удалось получить ItemID при вставке.");
                    }
                }
            }

            String insertStockSql = "INSERT INTO stock (itemID, quantity) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertStockSql)) {
                ps.setInt(1, newItemId);
                ps.setInt(2, quantity);
                ps.executeUpdate();
            }

            conn.commit();
            //конец транзакции

            return new Item(newItemId, name, price, quantity, barcode, categoryID, categoryName);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to insert new item", e);
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось добавить товар в базу данных");
            return null;
        }
    }


    @FXML
    private void editItem() {
        Item selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
//        помогите
        TextInputDialog nameDialog = new TextInputDialog(selected.getItemName());
        nameDialog.setTitle("Редактирование товара");
        nameDialog.setHeaderText("Редактирование названия");
        nameDialog.setContentText("Новое название:");
        var nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty()) return;
        String newName = nameResult.get();

        TextInputDialog priceDialog = new TextInputDialog(String.valueOf(selected.getItemPrice()));
        priceDialog.setTitle("Редактирование товара");
        priceDialog.setHeaderText("Редактирование цены");
        priceDialog.setContentText("Новая цена:");
        var priceResult = priceDialog.showAndWait();
        if (priceResult.isEmpty()) return;
        double newPrice = Double.parseDouble(priceResult.get());

        TextInputDialog qtyDialog = new TextInputDialog(String.valueOf(selected.getItemQuantity()));
        qtyDialog.setTitle("Редактирование товара");
        qtyDialog.setHeaderText("Редактирование количества");
        qtyDialog.setContentText("Новое количество:");
        var qtyResult = qtyDialog.showAndWait();
        if (qtyResult.isEmpty()) return;
        int newQty = Integer.parseInt(qtyResult.get());


        selected.setItemName(newName);
        selected.setItemPrice(newPrice);
        selected.setItemQuantity(newQty);


        updateItemInDB(selected);
        refresh();
    }

    @FXML
    private void addItem() {
        // Диалог добавления товара
        Dialog<Item> dialog = new Dialog<>();
        dialog.setTitle("Добавление товара");
        dialog.setHeaderText("Введите данные нового товара");

        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Поля ввода
        TextField nameField = new TextField();
        TextField priceField = new TextField();
        TextField qtyField = new TextField();
        ComboBox<Category> categoryCombo = new ComboBox<>(categories);
        TextField barcodeField = new TextField();

        nameField.setPromptText("Название");
        priceField.setPromptText("Цена");
        qtyField.setPromptText("Количество");
        categoryCombo.setPromptText("Выберите категорию");
        barcodeField.setPromptText("Штрих-код");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Цена:"), 0, 1);
        grid.add(priceField, 1, 1);

        grid.add(new Label("Количество:"), 0, 2);
        grid.add(qtyField, 1, 2);

        grid.add(new Label("Категория:"), 0, 3);
        grid.add(categoryCombo, 1, 3);

        grid.add(new Label("Штрих-код:"), 0, 4);
        grid.add(barcodeField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(nameField::requestFocus);

        // кнопка неактивна при пустом названии
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        nameField.textProperty().addListener((obs, oldVal, newVal) ->
                saveButton.setDisable(newVal.trim().isEmpty())
        );

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String name = nameField.getText().trim();
                    double price = Double.parseDouble(priceField.getText().trim());
                    int qty = Integer.parseInt(qtyField.getText().trim());
                    Category selectedCategory = categoryCombo.getValue();
                    String barcode = barcodeField.getText().trim();

                    if (selectedCategory == null) {
                        showAlert(Alert.AlertType.ERROR,
                                "Ошибка ввода",
                                "Пожалуйста, выберите категорию.");
                        return null;
                    }

                    int categoryId = selectedCategory.getId();
                    String categoryName = selectedCategory.getName();

                    return insertItemToDB(name, price, qty, categoryId, barcode, categoryName);
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR,
                            "Ошибка ввода",
                            "Цена, количество и ID категории должны быть числами.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newItem -> {
            if (newItem != null) {
                refresh();
            }
        });
    }

    @FXML
    private void deleteItem() {
        Item selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление товара");
        confirm.setHeaderText("Удалить товар \"" + selected.getItemName() + "\"?");

        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                deleteItemFromDB(selected);
                refresh();
            }
        });
    }









    @FXML
    private void addCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Добавление категории");
        dialog.setHeaderText("Создание новой категории");
        dialog.setContentText("Название категории:");

        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String name = result.get().trim();
        if (name.isEmpty()) {
            showAlert(Alert.AlertType.ERROR,
                    "Ошибка ввода",
                    "Название категории не может быть пустым.");
            return;
        }

        try (Connection conn = helper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO item_category (Name) VALUES (?) RETURNING CategoryID"
             )) {

            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("CategoryID");
                    Category newCategory = new Category(id, name);
                    categories.add(newCategory); // обновляем локальный список
                    showAlert(Alert.AlertType.INFORMATION,
                            "Категория добавлена",
                            "Категория \"" + name + "\" успешно добавлена.");
                    log.log(Level.INFO, name + "добавлена");
                } else {
                    throw new SQLException("Не удалось получить CategoryID при вставке категории");
                }
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to add category", e);
            showAlert(Alert.AlertType.ERROR,
                    "Ошибка базы данных",
                    "Не удалось добавить категорию в базу данных.");
        }
    }

    @FXML
    private void deleteCategory() {
        if (categories.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION,
                    "Нет категорий",
                    "В базе данных пока нет ни одной категории.");
            return;
        }

        ChoiceDialog<Category> dialog = new ChoiceDialog<>(categories.get(0), categories);
        dialog.setTitle("Удаление категории");
        dialog.setHeaderText("Выберите категорию для удаления");
        dialog.setContentText("Категория:");

        dialog.showAndWait().ifPresent(category -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Подтверждение удаления");
            confirm.setHeaderText("Удалить категорию \"" + category.getName() + "\"?");
            confirm.setContentText("Если к этой категории привязаны товары, удалить её не получится.");

            confirm.showAndWait().ifPresent(buttonType -> {
                if (buttonType != ButtonType.OK) {
                    return;
                }

                try (Connection conn = helper.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "DELETE FROM item_category WHERE CategoryID = ?"
                     )) {

                    ps.setInt(1, category.getId());
                    ps.executeUpdate();

                    categories.remove(category);
                    refresh();

                    showAlert(Alert.AlertType.INFORMATION,
                            "Категория удалена",
                            "Категория \"" + category.getName() + "\" успешно удалена.");

                } catch (SQLException e) {

                    if ("23503".equals(e.getSQLState())) {
                        showAlert(Alert.AlertType.WARNING,
                                "Удаление невозможно",
                                "Нельзя удалить категорию, так как к ней привязаны товары.");
                        log.log(Level.INFO,
                                "Failed to delete category, it is referenced by Items");
                    } else {
                        log.log(Level.SEVERE, "Failed to delete category", e);
                        showAlert(Alert.AlertType.ERROR,
                                "Ошибка базы данных",
                                "Не удалось удалить категорию.");
                    }
                }
            });
        });
    }
    //И с проектом всё  :)
}
