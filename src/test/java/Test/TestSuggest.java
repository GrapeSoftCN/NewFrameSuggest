package Test;

import common.java.httpServer.booter;
import common.java.nlogger.nlogger;

public class TestSuggest {
    public static void main(String[] args) {
        booter booter = new booter();
        try {
            System.out.println("Suggest");
            System.setProperty("AppName", "Suggest");
            booter.start(1007);
        } catch (Exception e) {
            nlogger.logout(e);
        }
    }
}
