#!/usr/bin/env python3

from sys import argv

instructions = {
    '>': 0x0,  '<': 0x1,
    '+': 0x2,  '-': 0x3,
    '.': 0x4,  ',': 0x5,
    '[': 0x6,  ']': 0x7,
}

if __name__ == '__main__':
    if len(argv) != 3:
        print(f'Usage: {argv[0]} in.bf out.bin')
        exit()
    
    with open(argv[1]) as in_f, open(argv[2], 'wb') as out_f:
        while True:
            c = in_f.read(1)
            if not c:
                break
            if c not in instructions:
                continue
            instr = instructions[c]
            out_f.write(bytes([instr]))