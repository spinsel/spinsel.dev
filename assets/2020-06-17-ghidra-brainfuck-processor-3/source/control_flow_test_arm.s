// +
ldr r1, [r0]
add r1, #1
str r1, [r0]

// -
ldr r1, [r0]
sub r1, #1
str r1, [r0]

loop_start:
// [
ldr r1, [r0]
cmp r1, #0
beq loop_end

// -
ldr r1, [r0]
sub r1, #1
str r1, [r0]

// ]
b loop_start
loop_end:

// +
ldr r1, [r0]
add r1, #1
str r1, [r0]
