package org.example.stationery_seller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CashierController {

    private static final Logger log = Logger.getLogger(CashierController.class.getName());
    @FXML private TilePane itemsList;
    @FXML private VBox shopingCart;
    @FXML private TextField searchField;
    @FXML private Label totalPriceLabel;
    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private int UserId;
    private String fullName;

    ConnectionHelper helper = ConnectionHelper.getInstance();

    @FXML
    void onAccountExit() {
        try {
            Stage stage = (Stage) totalPriceLabel.getScene().getWindow();

            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("Login.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1024, 768);

            stage.setTitle("Канцелярские товары");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Can't change scene", e);
        }
    }

    @FXML void onExit(){
        Platform.exit();
    }
    @FXML
    private void onAbout() {
        showAlert(Alert.AlertType.INFORMATION, "О программе", "Приложение для продажи канцелярских товаров.\n Автор: Татарских Д.Н.");
    }

    //управление корзиной
    @FXML private void addItemInShopingCart(Item item, int quantity){
        CartItem cartItem = new CartItem(item, quantity);
        cart.add(cartItem);
        HBox hbox = new HBox();
        hbox.setStyle(
                "-fx-background-color: #f5f5f5;" +
                        "-fx-padding: 5;" +
                        "-fx-background-radius: 5;" +
                        "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 5;"
        );
        hbox.setPrefWidth(280);
        hbox.setSpacing(10);
        hbox.setAlignment(Pos.CENTER_LEFT);

        hbox.setUserData(cartItem);

        CheckBox checkbox = new CheckBox();
        Label nameLabel = new Label(item.getItemName());

        nameLabel.setEllipsisString("...");
        nameLabel.setPrefWidth(150);
        nameLabel.setMaxWidth(150);
        nameLabel.setWrapText(false);

        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label quantityLabel = new Label(quantity + " шт");
        quantityLabel.setAlignment(Pos.CENTER_RIGHT);

        Label priceLabel = new Label(item.getItemPrice() * quantity + "₽");
        priceLabel.setAlignment(Pos.CENTER_RIGHT);

        hbox.getChildren().addAll(checkbox, nameLabel, quantityLabel, priceLabel);

        shopingCart.getChildren().add(hbox);
        updateTotalPrice();
    }

    //удаление выбранных галочкой товаров
    @FXML
    private void removeSelectedItems() {
        var toRemove = shopingCart.getChildren().filtered(node -> {
            if (node instanceof HBox hbox) {
                for (var child : hbox.getChildren()) {
                    if (child instanceof CheckBox cb && cb.isSelected()) {
                        return true;
                    }
                }
            }
            return false;
        });
        for (var node : toRemove) {
            if (node instanceof HBox hbox) {
                CartItem cartItem = (CartItem) hbox.getUserData();
                if (cartItem != null) {
                    cartItem.getItem().setItemQuantity(
                            cartItem.getItem().getItemQuantity() + cartItem.getQuantity()
                    );
                    cart.remove(cartItem);
                }
            }
        }
        shopingCart.getChildren().removeAll(toRemove);
        updateItemsUI();
        updateTotalPrice();
    }

    private void updateTotalPrice() {
        double total = 0;
        for (CartItem cartItem : cart) {
            total += cartItem.getItem().getItemPrice() * cartItem.getQuantity();
        }
        totalPriceLabel.setText(String.format("%.2f ₽", total));
    }

    //создание карточек товара из базы данных
    @FXML
    private void loadItemsFromDB(){
        items.clear();
        try (Connection conn = helper.getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT i.ItemID, i.Name, i.Price, s.quantity, i.barcode, i.CategoryID " +
                             "FROM Items i JOIN stock s " +
                             "ON i.ItemID = s.itemID " +
                             "WHERE s.quantity > 0 ORDER BY i.Name"
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
                        null
                );
                items.add(item);
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database connection error", e);
            showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось загрузить товары из базы данных");
        }

        for (CartItem cartItem : cart) {
            for (Item item : items) {
                if (item.getItemID() == cartItem.getItem().getItemID()) {
                    item.setItemQuantity(item.getItemQuantity() - cartItem.getQuantity());
                    break;
                }
            }
        }

        updateItemsUI();
    }

    private void updateItemsUI() {
        itemsList.getChildren().clear();
        for (Item item : items) {
            if (item.getItemQuantity() > 0) {
                itemsList.getChildren().add(createItemCard(item));
            }
        }
    }

    @FXML
    private void searchItems() {
        String query = searchField.getText().trim().toLowerCase();
        itemsList.getChildren().clear();

        if (query.isEmpty()) {
            updateItemsUI();
            return;
        }

        for (Item item : items) {
            if (item.getItemName().toLowerCase().contains(query) && item.getItemQuantity() > 0) {
                itemsList.getChildren().add(createItemCard(item));
            }
        }
    }

    private Button createItemCard(Item item) {
        Label nameLabel = new Label(item.getItemName());
        Label priceLabel = new Label(item.getItemPrice() + "₽");
        Label quantityLabel = new Label(item.getItemQuantity() + " шт");

        VBox vBox = new VBox(nameLabel, priceLabel, quantityLabel);
        vBox.setPrefWidth(100);

        Button itemCardButton = new Button();
        itemCardButton.setGraphic(vBox);
        itemCardButton.setUserData(item);

        itemCardButton.setOnAction(event -> {
            Item selectedItem = (Item) itemCardButton.getUserData();
            showQuantityDialogAndAddToCart(selectedItem);
        });

        return itemCardButton;
    }


    private void showQuantityDialogAndAddToCart(Item item) {
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Выбор количества");
        dialog.setHeaderText(item.getItemName());
        dialog.setContentText("Введите количество (макс. " + item.getItemQuantity() + "):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                int quantity = Integer.parseInt(input);
                if (quantity > 0 && quantity <= item.getItemQuantity()) {

                    item.setItemQuantity(item.getItemQuantity() - quantity);
                    addItemInShopingCart(item, quantity);
                    log.info("ID: " + item.getItemID());
                    updateItemsUI();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Ошибка","Количество должно быть от 1 до " + item.getItemQuantity());
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Ошибка","Введите корректное число");
            }
        });
    }



    @FXML
    private void sellItems() {
        if (cart.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Ошибка","Корзина пуста!");
            return;
        }
        Connection connection = null;
        try {
            connection = helper.getConnection();

            //начало транзакции
            connection.setAutoCommit(false);

            //вставка в таблицу receipts
            int cashierId = UserId;
            double total = cart.stream().mapToDouble(ci -> ci.getItem().getItemPrice() * ci.getQuantity()).sum();
            String insertReceiptSQL = "INSERT INTO receipts (cashierID, shopName, total) VALUES (?, 'Вятская', ?)";

            int receiptId;
            try (PreparedStatement stmt = connection.prepareStatement(insertReceiptSQL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, cashierId);
                stmt.setDouble(2, total);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        receiptId = rs.getInt(1);
                    } else {
                        throw new SQLException("Failed to get receipt ID, no rows affected.");
                    }
                }
            }

            String insertReceiptItemSQL = "INSERT INTO receipt_items (receiptID, itemID, quantity, price) VALUES (?, ?, ?, ?)";
            String updateStockSQL = "UPDATE stock SET quantity = quantity - ? WHERE itemID = ?";

            try (PreparedStatement insertStmt = connection.prepareStatement(insertReceiptItemSQL);
                 PreparedStatement updateStmt = connection.prepareStatement(updateStockSQL)) {

                for (CartItem cartItem : cart) {
                    Item item = cartItem.getItem();

                    // receipt_items
                    insertStmt.setInt(1, receiptId);
                    insertStmt.setInt(2, item.getItemID());
                    insertStmt.setDouble(3, cartItem.getQuantity());
                    insertStmt.setDouble(4, item.getItemPrice());
                    insertStmt.addBatch();

                    // stock
                    updateStmt.setDouble(1, cartItem.getQuantity());
                    updateStmt.setInt(2, item.getItemID());
                    updateStmt.addBatch();
                }

                insertStmt.executeBatch();
                updateStmt.executeBatch();
            }

            connection.commit(); // Завершение транзакции

            showReceiptExportDialog();
            showAlert(Alert.AlertType.INFORMATION, "Продажа", "Продажа успешно осуществлена");
            cart.clear();
            shopingCart.getChildren().clear();
            updateTotalPrice();
            loadItemsFromDB();

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database transaction error during sellItems", e);
            if (connection != null) {
                try {
                    connection.rollback();
                    log.info("Transaction rolled back.");
                } catch (SQLException rbEx) {
                    log.log(Level.SEVERE, "Error during transaction rollback", rbEx);
                }
            }
            if (e.getMessage().contains("Недостаточно товара")) {
                showAlert(Alert.AlertType.ERROR, "Ошибка", "Недостаточно товара на складе для продажи!");
                log.log(Level.SEVERE, e.getMessage());
            }
            else{
            showAlert(Alert.AlertType.ERROR, "Ошибка","Произошла ошибка при продаже товаров: " + e.getMessage());
            log.log(Level.SEVERE, e.getMessage());
            }

            cart.clear();
            shopingCart.getChildren().clear();
            updateTotalPrice();
            loadItemsFromDB();

        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ex) {
                    log.log(Level.SEVERE, "Error closing connection", ex);
                }
            }
        }

    }


    private void showReceiptExportDialog(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить чек");
        fileChooser.setInitialFileName("чек_продажи.txt");

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Текстовые файлы (*.txt)", "*.txt");
        fileChooser.getExtensionFilters().add(extFilter);

        File file = fileChooser.showSaveDialog(totalPriceLabel.getScene().getWindow());

        if (file != null) {
            writeReceiptToFile(file);
        }
    }

    private void writeReceiptToFile(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("");
            writer.println("          Квитанция об оплате        ");
            writer.println("");
            writer.printf("Дата и время: %tD %tT%n", new java.util.Date(), new java.util.Date());
            writer.printf("Продавец: %s%n",fullName);
            writer.println("");
            writer.println("Товар              | Количество | Цена");
            writer.println("");

            double total = 0;
            for (CartItem item : cart) {
                writer.printf("%-18s | %-10s | %-6.2f%n",
                        item.getItem().getItemName(),
                        item.getQuantity(),
                        item.getItem().getItemPrice() * item.getQuantity()
                );
                total += item.getItem().getItemPrice() * item.getQuantity();
            }

            writer.println("");
            writer.printf("Итого: %.2f ₽%n", total);
            writer.println("");

            showAlert(Alert.AlertType.INFORMATION, "Экспорт чека", "Чек успешно сохранен в: " + file.getAbsolutePath());

        } catch (IOException e) {
            log.log(Level.SEVERE, "Ошибка при записи чека в файл", e);
            showAlert(Alert.AlertType.ERROR, "Ошибка экспорта", "Не удалось сохранить чек: " + e.getMessage());
        }
    }



    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setUserId(int userId, String fullName){
        this.UserId = userId;
        this.fullName = fullName;
        loadItemsFromDB();
    }

}
