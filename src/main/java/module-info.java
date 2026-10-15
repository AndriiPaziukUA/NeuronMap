module com.example.neuronmap {
    requires javafx.controls;
    requires java.sql;
    requires java.xml;
    requires java.logging;
    requires org.xerial.sqlitejdbc;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    opens com.example.neuronmap.windowing to com.sun.jna;

    exports com.example.neuronmap.app;
}
