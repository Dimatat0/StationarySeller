module org.example.stationery_seller {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires java.sql;

    opens org.example.stationery_seller to javafx.fxml;
    exports org.example.stationery_seller;
}