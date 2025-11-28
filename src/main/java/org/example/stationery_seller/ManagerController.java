package org.example.stationery_seller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
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

    private final ObservableList<Item> items = FXCollections.observableArrayList();

    private int UserId;
    private String fullName;
    private static final Logger log = Logger.getLogger(ManagerController.class.getName());
    ConnectionHelper helper = ConnectionHelper.getInstance();


    public void setUserId(int userId, String fullName){
        this.UserId = userId;
        this.fullName = fullName;
        loadItemsFromDB();
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
                     "SELECT i.ItemID, i.Name, i.Price, s.quantity FROM Items i JOIN stock s ON i.ItemID = s.itemID ORDER BY i.ItemID"
             );
             ResultSet result = statement.executeQuery()
        ) {
            while (result.next()){
                Item item = new Item(
                        result.getInt("ItemID"),
                        result.getString("Name"),
                        result.getDouble("Price"),
                        result.getInt("Quantity")
                );
                items.add(item);
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database connection error");
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить товары из базы данных");
        }

        updateItemsUI();
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
                                int categoryId,
                                String barcode) {

        try (Connection conn = helper.getConnection()) {

            //начало транзакции
            conn.setAutoCommit(false);

            int newItemId;

            String insertItemSql = "INSERT INTO Items (Name, Price, CategoryID, barcode) " +
                    "VALUES (?, ?, ?, ?) RETURNING ItemID";
            try (PreparedStatement ps = conn.prepareStatement(insertItemSql)) {
                ps.setString(1, name);
                ps.setDouble(2, price);
                ps.setInt(3, categoryId);
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

            return new Item(newItemId, name, price, quantity);

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
        TextField categoryField = new TextField();
        TextField barcodeField = new TextField();

        nameField.setPromptText("Название");
        priceField.setPromptText("Цена, например 99.90");
        qtyField.setPromptText("Количество, например 10");
        categoryField.setPromptText("ID категории");
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

        grid.add(new Label("ID категории:"), 0, 3);
        grid.add(categoryField, 1, 3);

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
                    int categoryId = Integer.parseInt(categoryField.getText().trim());
                    String barcode = barcodeField.getText().trim();

                    return insertItemToDB(name, price, qty, categoryId, barcode);
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

                items.add(newItem);
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

    //TODO: Сделать кнопки добавления и удаления категорий
    //TODO: Сделать отображение имени категории в таблице
    //TODO: Сделать так, чтобы при добавлении товара, вкладка с категориями выпадала и можно выбрать
    //И с проектом всё :)
}
