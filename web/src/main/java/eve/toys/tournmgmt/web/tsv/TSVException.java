package eve.toys.tournmgmt.web.tsv;

public class TSVException extends RuntimeException {
    public TSVException(String msg) {
        super(msg);
    }

    public TSVException(Exception e) {
        super(e);
    }
}
