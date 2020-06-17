package brainfuck;

public class UnmatchedBranchException extends RuntimeException {
    public UnmatchedBranchException() {
        super();
    }

    public UnmatchedBranchException(long address){
        super("Unmatched branch at address 0x" + Long.toHexString(address));
    }
}