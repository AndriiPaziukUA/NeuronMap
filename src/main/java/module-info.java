module com.example.neuronmap {
    requires javafx.controls;
    requires java.sql;
    requires java.xml;
    requires java.logging;
    requires java.management;
    requires jdk.management;
    requires org.xerial.sqlitejdbc;
    requires org.aspectj.runtime;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.jul;

    exports com.example.neuronmap.app;
}
