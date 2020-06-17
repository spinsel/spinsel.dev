package brainfuck;

import java.util.Stack;

import ghidra.app.services.AbstractAnalyzer;
import ghidra.app.services.AnalysisPriority;
import ghidra.app.services.AnalyzerType;
import ghidra.app.util.importer.MessageLog;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.address.AddressOutOfBoundsException;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

public class BrainfuckAnalyzer extends AbstractAnalyzer {
    private final static String NAME = "Brainfuck Branch Destination Resolver";
    private final static String DESCRIPTION = "Computes branch destinations for control flow instructions ([ and ]) and writes them to bdest memory";

    private final static String PROCESSOR_NAME = "brainfuck";

    private final static int OPEN_LOOP = 0x6;
    private final static int CLOSE_LOOP = 0x7;

    private final static int BDEST_WORD_SIZE = 2;

    public BrainfuckAnalyzer() {
        super(NAME, DESCRIPTION, AnalyzerType.BYTE_ANALYZER);
        setDefaultEnablement(true);
        setSupportsOneTimeAnalysis(true);
        setPriority(AnalysisPriority.DISASSEMBLY.before());
    }

    @Override
    public boolean canAnalyze(Program program) {
        String procName = program.getLanguage().getProcessor().toString();
        return procName.equals(PROCESSOR_NAME);
    }

    private byte readInstruction(MemoryBlock block, long offset)
            throws MemoryAccessException {
        Address addr = block.getStart().add(offset);
        return block.getByte(addr);
    }

    private void writeBranchDest(MemoryBlock block, long offset, long dest)
            throws MemoryAccessException {
        Address addr = block.getStart().add(offset * BRANCH_DESTS_WORD_SIZE);
        byte[] bytes = {
                (byte) ((dest >> 0) & 0xff),
                (byte) ((dest >> 8) & 0xff)
            };
        
        block.putBytes(addr, bytes);
    }

    private boolean resolveBranchDests(Program program, TaskMonitor monitor)
            throws CancelledException, MemoryAccessException {
        Memory mem = program.getMemory();
        MemoryBlock rom = mem.getBlock("rom");
        MemoryBlock bdest = mem.getBlock("bdest");
        
        if(bdest.getSize() < rom.getSize())
            throw new AddressOutOfBoundsException("The bdest memory can't be smaller than the rom memory.");

        monitor.initialize(rom.getSize());

        Stack<Long> addrStack = new Stack<Long>();
        for(long addr = 0; addr < rom.getSize(); addr++){
            monitor.checkCanceled();
            monitor.incrementProgress(1);

            byte instr = readInstruction(rom, addr);
            switch(instr){
                case OPEN_LOOP:
                    addrStack.push(addr);
                    break;
                
                case CLOSE_LOOP:
                    if(addrStack.empty())
                        throw new UnmatchedBranchException(addr);

                    long openInstr = addrStack.pop();
                    writeBranchDest(bdest, addr, openInstr);
                    writeBranchDest(bdest, openInstr, addr + 1);
                    break;
            }
        }
        
        if(!addrStack.empty())
            throw new UnmatchedBranchException();

        return true;
    }

    @Override
    public boolean added(Program program, AddressSetView set, TaskMonitor monitor, MessageLog log)
            throws CancelledException {
        try {
            return resolveBranchDests(program, monitor);
        } catch(CancelledException e) {
            throw e;
        } catch(Exception e) {
            log.appendException(e);
            return false;
        }
    }
}
