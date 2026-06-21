module com.example.schoolbus {
  requires javafx.controls;
  requires javafx.fxml;
  requires com.google.gson;
  requires java.prefs; // Hozzáadva a Fast Load memóriához!

  opens com.example.schoolbus to javafx.fxml;
  exports com.example.schoolbus;

  opens com.example.schoolbus.controller to javafx.fxml;
  exports com.example.schoolbus.controller;

  opens com.example.schoolbus.model to com.google.gson;
  exports com.example.schoolbus.model;
}