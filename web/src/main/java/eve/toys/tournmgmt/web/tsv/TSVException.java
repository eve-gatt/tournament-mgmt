package eve.toys.tournmgmt.web.tsv;

public class TSVException extends Exception {
    public TSVException(String msg) {
        super(msg);
    }

    public TSVException(Exception e) {
        super(e);
    }
}
