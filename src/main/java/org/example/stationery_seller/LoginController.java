package org.example.stationery_seller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {
    private static final Logger log = Logger.getLogger(LoginController.class.getName());
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    private int UserID;

    @FXML
    private void onLogin() {

        String username = usernameField.getText();
        String password = passwordField.getText(); // TODO: заменить на hash
        ConnectionHelper.init("jdbc:postgresql://192.168.0.12:5432/shop", "postgres","51500990"); //TODO: сделать поле для подключения на этапе регистрации
        ConnectionHelper helper = ConnectionHelper.getInstance();




        try {
            Connection conn = helper.getConnection();
            PreparedStatement statement = conn.prepareStatement(
                    "SELECT UserID, RoleID FROM Users WHERE login = ? AND password_hash = ?"
            );
            statement.setString(1, username);
            statement.setString(2, password); // TODO: заменить на hash

            ResultSet result = statement.executeQuery();
            if (result.next()) {
                int roleId = result.getInt("RoleID");
                int userId = result.getInt("UserID");

                try{
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("CashierView.fxml"));
                    Parent mainView = loader.load();

                    CashierController controller = loader.getController();
                    controller.setUserId(userId);

                    Scene scene = new Scene(mainView,1024,768);
                    Stage stage = (Stage) usernameField.getScene().getWindow();
                    stage.setScene(scene);
                }
                catch (Exception ex){
                    log.log(Level.SEVERE,"Loader fail", ex);
                }

                log.info(username + " logged in");
                errorLabel.setText("");

            } else {
                errorLabel.setText("Неверный логин или пароль");
                log.info(username + " is trying to log in");
            }

        }
        catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка подключения", e.getMessage());
        }
        }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}