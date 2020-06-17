---
title: "Implementing a brainfuck CPU in Ghidra - part 2: Decompilation of pointer and arithmetic instructions"
tags: Ghidra
series: ghidra_brainfuck
series_part: p0002
---

{%-include utils.html-%}
{%-include series.html-%}

In the [previous post][prev_post] we created a processor module for a compiled version of brainfuck. Right now, it can only disassemble brainfuck binaries, but not decompile them. In this post we'll take a look at the decompilation part of the processor module. More specifically, we'll implement the pointer (`>` and `<`) and arithmetic (`+` and `-`) instructions. We'll handle the other instructions later.

# Auto analysis
Before we get to writing semantics for the instructions, let's first address a problem from the previous post: the auto analysis does not disassemble the binary.

When a binary is opened with the code browser, Ghidra asks if it should perform auto analysis on the binary. Upon clicking yes the analysis options menu opens, showing all the available analyzers for this binary. Among these is the *Disassemble Entry Points* analyzer:

![The Disassemble Entry Points analyzer]({{page_images}}/analysis_disas_entry_points.png)

The purpose of this analyzer is disassembling code at the entry points of the binary. If we mark address `0x0000` as an entry point, this analyzer should automatically disassemble it when we perform the auto analysis. Marking entry points is very simple, though there is no documentation on it. Add the following code to the `<processor_spec>` tag in `brainfuck.pspec`:

```xml
<default_symbols>
    <symbol name="start" address="rom:0x0000" type="code" entry="true"/>
</default_symbols>
```

This creates a symbol `start` (you can rename it) at address `0x0000` and marks it as entry point. If we now delete `mul2.bin` from Ghidra, reimport it[^reimport] and run the auto analysis, the binary will be decompiled automatically. The stack analyzer throws an error, because there is no stack. This is safe to ignore. (Optionally, this analyzer can be disabled in the analysis options menu.)

# Pointer instructions
The semantics of the pointer instructions (`>` and `<`) are the simplest to implement. Let's implement the semantics of `>` first. The semantics of `<` can be implemented very similarly.

The `>` instruction adds 1 to the `ptr` register. This can be represented using the SLEIGH operator `+`[^sleigh_spec]:

```
:> is op=0x0 {
    ptr = ptr + 1;
}
```

The semantics for `<` are almost identical, except that the `+` is now a `-`:
```
:< is op=0x1 {
    ptr = ptr - 1;
}
```

# Arithmetic instructions
The arithmetic instructions (`+` and `-`) are slightly more complex than the pointer instructions. They do not operate on a register, but on memory (`ram`) pointed to by the `ptr` register. The arithmetic instructions can be broken up into three operations:
1. Load the value of the current cell from `ram` into a temporary variable (let's name it `c` for cell). In SLEIGH this looks like: `local c:2 = *[ram]ptr;`
2. Increment or decrement the cell value by one: `c = c + 1` or `c = c - 1`
3. Store the new value in the current cell: `*[ram]ptr = c;`

Thus, we have:
```
:+ is op=0x2 {
    local c:2 = *[ram]ptr;
    c = c + 1;
    *[ram]ptr = c;
}

:- is op=0x3 {
    local c:2 = *[ram]ptr;
    c = c - 1;
    *[ram]ptr = c;
}
```

# P-code and varnodes
*This section covers the internal representation of instruction semantics in Ghidra. It isn't strictly necessary to understand this in order to create a processor module, so you may skip it. I do advise you to read it though, as it can be very useful to have some knowledge of the underlying representation that Ghidra uses.*

As described in the previous post, Ghidra doesn't use the `.slaspec` directly. Instead it uses the `.sla` file, which is compiled from the `.slaspec` file by the SLEIGH compiler[^slaspec_comp]. The `.slaspec` is readable and human-friendly, while the `.sla` is very verbose and more machine-friendly. Similarly, Ghidra doesn't use SLEIGH operators and symbols directly to describe instructions, but p-code operations and varnodes.

While the SLEIGH operators have a C-like syntax, p-code operations have an assembly-like syntax. Consider the following SLEIGH semantics, for example:

```
local x:2;
x = 36;
x = x + 44;
```

This may be translated to the following p-code[^pcode_ref]:
```
$U10:2 = COPY 36:2
$U10:2 = INT_ADD $U10, 44:2
```

The SLEIGH compiler has created a varnode in the **`unique`** address space for the temporary symbol `x` in the SLEIGH semantics. This varnode starts at address `0x10`, has a size of 2 bytes and is denoted by `$U10` or `$U10:2`.

The first operation, `COPY`, copies the value `36` into `$U10`. The second operation, `INT_ADD`, adds 44 to `$U10` and stores the result in `$U10`.

Now that we know more about the translation from SLEIGH semantics to p-code, let's view the generated p-code for our implemented SLEIGH semantics.

If we open `mul2.bin` in the code browser, we can view the p-code of the `>`, `<`, `+` and `-` instructions by enabling the p-code field in the listing. First, click the `Edit the Listing fields` button in the top right corner of the listing. The icon looks like a wall above an arrow:

![The 'Edit the Listing fields' button]({{page_images}}/edit_listing.png)

A menu should open now. Click the `Instruction/Data` tab:

!['Instruction/Data' tab in the 'Edit the Listing fields' menu]({{page_images}}/edit_listing_pcode.png)

Right-click the `PCode` field and enable it. The listing now shows the p-code for every instruction that has p-code associated with it:

![Listing with p-code]({{page_images}}/listing.png)

You may also enable the more verbose raw p-code. To do this go to `Edit → Tool Options...`, then `Listing Fields → Pcode Field` and enable the `Display Raw Pcode` option. The raw p-code for the `+` looks like this:

![Raw p-code]({{page_images}}/raw_pcode.png)

You'll rarely need to look at (raw) p-code during disassembly and decompilation of real-world programs. Still, it can be useful to have this field enabled during the development of a processor module, when emulating code or developing abstract analyzers. In most other cases you needn't worry about these low-level details.

# Decompilation
Ghidra should now be able to decompile any brainfuck programs that use (only) the `>`, `<`, `+` and `-` instructions. Here is such a program, `mark_uneven.bf`:

```brainfuck
>+>
>+>
>+>
```

This program increments the first three cells that have an uneven address. It's not particularly useful, but it only uses instructions that we have described the semantics of.

We can compile it (`./bfc.py mark_uneven.bf mark_uneven.bin`) and load it into Ghidra. Thanks to the entry point definition in the `.pspec`, Ghidra creates a function called `start` at `0x0000` and disassembles it. When we click the function, the decompile window should show a decompilation. Unfortunately this is not what happens. What happens is that the decompiler shows an error:

![The decompiler shows an error]({{page_images}}/decompiler_error_stack.png)

This is because of the following bug: the decompiler refers to the stack[^stack_ref], but we haven't specified one, because there is no stack in brainfuck[^stack_bug]. This causes the decompiler to crash. To work around this, we can create a dummy register and address space in the `.slaspec`:

```
define space dummy_space type=ram_space size=2 wordsize=2;
...
define register offset=0x00 size=2 [pc ptr dummy_reg];
```

We'll use `dummy_space` and `dummy_reg` anytime a register or address space is required that doesn't exist in the brainfuck architecture. The stack pointer can be defined by adding the following code to the `<compiler_spec>` tag in the `.cspec`:

```xml
<stackpointer register="dummy_reg" space="dummy_space" growth="negative"/>
```

We're not done yet (but almost, so don't worry!). If we'd run the decompiler now, it would - again - show an error:

![The decompiler shows an error]({{page_images}}/decompiler_error_addr.png)

This error is more descriptive than the previous one: the decompiler won't decompile instructions at address `0x0000`, because the memory hasn't been marked as global. Any memory *not* marked as global is assumed to be temporary. Hence, it is important that we mark `rom` and `ram` as global. Add the following code to the `<compiler_spec>` tag in the `.cspec`:

```xml
<global>
    <range space="rom"/>
    <range space="ram"/>
</global>
```

While we're at it, we can also add `ram` memory to the `.pspec` so that it shows up in the program tree. (`rom` is already shown in the program tree, because it is the default address space.) Add this to the `<processor_spec>` tag in the `.pspec`:

```xml
<default_memory_blocks>
    <memory_block name="ram" start_address="ram:0x0000" length="0x1000" mode="rw"/>
</default_memory_blocks>
```

Now, the decompiler is able to decompile the binary:

![Decompilation of mark_uneven.bin]({{page_images}}/decompilation.png)

It shows two warnings, because the function unexpectedly terminates, instead of returning, endlessly looping or calling to a non-returning function (e.g. `exit`). These warnings can be safely ignored.

# Cleaning up decompilation
The current decompilation doesn't look very good, but we can improve it. The increments are represented in the decompilation by:

```c
*(short *)(in_ptr + x) = *(short *)(in_ptr + x) + 1
```

This can be drastically improved by assigning an initial value to the `ptr` register. This value is called an **assumption**. Press `ctrl + R` on the start of the function and set the value of `ptr (16)` to `0`. The decompilation looks a lot better now:

![Decompilation of mark_uneven.bin after assuming ptr to be 0]({{page_images}}/decompilation_with_assumption.png)

Instead of setting the `ptr` value to `0` for every brainfuck binary we want to disassemble, we can also add some code to `<processor_spec>` in the `<processor_spec>` tag of the `.pspec` (or `.cspec`, which also accepts the `<context_data>` tag):

```xml
<context_data>
    <tracked_set space="rom" first="0x0000" last="0x0000">
        <set name="ptr" val="0"/>
    </tracked_set>
</context_data>
```

This code lets Ghidra assume that the value of `ptr` equals `0` at the function starting at address `0x0000`, also known as the entry point in this case.

If we now reimport the binary, `ptr` is automatically assumed to be `0` at address `0x0000`.

The final step in cleaning up the decompilation is marking the first three uneven addresses as data (`uint16_t` or `ushort`) and naming them. This results in the following readable decompilation:

![Decompilation of mark_uneven.bin after marking addresses as data]({{page_images}}/decompilation_data_marked.png)

An even better decompilation would be the following:

```c
void start(void){
    uneven_1 = 1;
    uneven_3 = 1;
    uneven_5 = 1;
}
```

This would require Ghidra to assume that `ram` is zero at address `0x0000`. I don't think this is possible. (I hope [Cunningham's Law][cunningham_law] holds in this case and someone proves me wrong.)

That's it for this post. We've implemented decompilation of the pointer and arithmetic instructions and cleaned up the decompilation. We've also taken a quick look at Ghidra's internal representation of instruction semantics (p-code). In the [next post][next_post], we'll complete the semantics for the brainfuck instruction set by writing code for the I/O (`.` and `,`) and control flow (`[` and `]`) instructions. See you then!

You can find the final code for this post [here][final_code].

# Footnotes
[^reimport]: From now on it is assumed that you reimport binaries everytime a change is made to the `.ldefs`, `.pspec`, `.cspec` and in some cases even the `.slaspec`.
[^sleigh_spec]: Refer to [*SLEIGH - 7.7. The Semantic Section*][sleigh_semantics] for information on the semantics section of constructors.
[^pcode_ref]: See the [*P-Code Reference Manual*][pcode_ref] and [*SLEIGH - 9. P-code Tables*][pcode_table], both part of the [*Ghidra Language Specification*][lang_spec].
[^slaspec_comp]: This compilation is done automatically by Ghidra when it encounters a `.slaspec`, so there is no need for us to compile it.
[^stack_ref]: The decompiler dereferences pointers to the stack space in a *lot* of places. For example, [`coreaction.cc:1644`][coreaction] calls a function `gather` with a pointer to the stack space. Since there is no stack space defined, this pointer is `NULL`. `gather` dereferences the pointer at [`varmap.cc:542`][varmap].  The dereference of this `NULL` pointer will result in a [segmentation fault][segfault]: The decompiler dies.
[^stack_bug]: It could be argued whether this is really a bug, as virtually every architecture uses a stack.

[prev_post]: {{series_posts[0].url}}
[cunningham_law]: https://meta.wikimedia.org/wiki/Cunningham%27s_Law
[next_post]: {{series_posts[2].url}}
[final_code]: {{site.github_repo}}/tree/master{{page_assets}}/source

[sleigh_semantics]: /assets/{{series_posts[0].id | slugify}}/ghidra_docs/language_spec/html/sleigh_constructors.html#sleigh_semantic_section
[pcode_ref]: /assets/{{series_posts[0].id | slugify}}/ghidra_docs/language_spec/html/pcoderef.html
[pcode_table]: /assets/{{series_posts[0].id | slugify}}/ghidra_docs/language_spec/html/sleigh_ref.html
[lang_spec]: /assets/{{series_posts[0].id | slugify}}/ghidra_docs/language_spec/index.html
[coreaction]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Features/Decompiler/src/decompile/cpp/coreaction.cc#L1644
[varmap]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Features/Decompiler/src/decompile/cpp/varmap.cc#L542
[segfault]: https://en.wikipedia.org/wiki/Segmentation_fault