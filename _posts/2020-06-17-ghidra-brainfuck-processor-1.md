---
title: "Implementing a brainfuck CPU in Ghidra - part 1: Setup and disassembly"
tags: Ghidra
series: ghidra_brainfuck
series_part: p0001
---

{%-include utils.html-%}
{%-include series.html-%}

Ghidra supports quite some processors out of the box, but if you come across a somewhat obscure architecture, chances are you'll have to write the processor module yourself (if someone else hasn't published it on the internet already). At first glance, it may seem daunting: it's not clear where to start and the documentation is often lacking. That's why I decided to write this blog post series. The goal is to create a Ghidra processor module for a compiled version of the esoteric programming language brainfuck, from scratch. The sources I used are the *Ghidra Language Specification*[^lang_spec], the *Compiler Specification*[^comp_spec] and the source code[^ghidra_git].

# Brainfuck
Brainfuck is a very minimal language consisting of only eight instructions. See the [wikipedia page][wiki_bf] for a full description of the language. My brainfuck variant deviates slightly from the original:

- The memory array is called `ram` memory and consists of `0x10000` 16-bit cells. The pointer `ptr` (16-bit) points to the current cell.
- Instructions are compiled to a binary format, using the following translation table:
  
  | Instruction | Opcode |
  |-------------|--------|
  | `>`         | `0x0`  |
  | `<`         | `0x1`  |
  | `+`         | `0x2`  |
  | `-`         | `0x3`  |
  | `.`         | `0x4`  |
  | `,`         | `0x5`  |
  | `[`         | `0x6`  |
  | `]`         | `0x7`  |
  {: .small-table }

  Compiled instructions are 8-bit wide. The four least significant bits make up the opcode, the other bits are unspecified and should be set to zero.
- Compiled instructions live in `rom` memory. The program counter `pc` (16-bit) points to the current instruction.

I created a simple brainfuck compiler ([`bfc.py`][bfc_src]) that takes a brainfuck file and outputs a binary. Non-instruction characters (anything but `><+-.,[]`) are interpreted as comments and will not be compiled. I didn't bother to write a VM that executes compiled brainfuck, although it wouldn't be hard to write yourself. (If you do, I'd love to see it.)

Let's compile a simple program to see the compiler in action. [`mul2.bf`][mul_src] takes some user input, multiplies it by two and prints the result. The source code is as follows:

```brainfuck
,          store user input in #0
[>++<-]    #1 = #0 * 2
>.         move to #1 and print its content
```

Compile it to `mul2.bin` and show the generated binary:

```bash
$ ./bfc.py mul2.bf mul2.bin
$ hexdump -C mul2.bin
00000000  05 06 00 02 02 01 03 07  00 04                    |..........|
0000000a
```

The rest of this blog post series will be dedicated to reversing and analyzing compiled brainfuck binaries like `mul2.bin`.

# Preparation
We'll be using [Eclipse][eclipse] to create the processor module. After installing Eclipse, we're ready to add the GhidraDev extension to Eclipse. The extension can be found in `Extensions/Eclipse/GhidraDev/` in the Ghidra installation directory, along with some documentation on how to install this extension. The extension can be added by opening the `Help → Install New Software...` menu in Eclipse and selecting the GhidraDev zip. The documentation covers this in more detail.

Although we're using Eclipse for development, you can use any other IDE of your preference for *editing* the files. I recommend VS Code with the [SleigHighlight][sleigh_highlight] extension for syntax highlighting of `.slaspec` files. XML syntax highlighting of `.ldefs`, `.pspec` and `.cspec` files is done in VS Code by marking them as XML in the bottom right corner. This is not required, it just eases the development of processor modules.

# Creating a project

Create a new project by going to `File → New → Project` and select the Ghidra Module Project wizard 🧙.

![Expand the Ghidra folder and select Ghidra Module Project]({{page_images}}/project_wizard.png "Select the Ghidra Module Project wizard")

Specify a name and directory for this project and hit next. The next screen lets us choose the components we want the wizard to add to our project. We only need the analyzer and the processor. Disable the others.

![]({{page_images}}/project_template.png "Select the analyzer and processor")

If you haven't used Eclipse with Ghidra before, you'll need to specify the root directory of your Ghidra installation and hit next. The last step lets us enable Python support. We don't need this, so leave the checkbox unchecked.

Then hit finish. We've now created our Ghidra project!

# Creating the language definitions
The processor lives in `data/languages`. The wizard has already populated this folder with definitions for the (hypothetical) skel processor, which are of no use to us. Delete these files.

![]({{page_images}}/languages_content.png "Delete the skel processor definitions")

In Ghidra you don't define processors, but languages instead. A **language** specifies a variant of a processor family. For example, [`x86.ldefs`][x86_ldefs] contains specifications for 16-bit, 32-bit and 64-bit variants of the x86 processor architecture. Ghidra interprets all files with an `.ldefs` extension as a language definitions file[^lang_prov]. The format of this file is not really documented, but there is an XML schema file (written in [RELAX NG][wiki_relax_ng]) that describes the structure of `.ldefs` files: [`language_definitions.rxg`][lang_def_rxg]. Using this schema we can compose `brainfuck.ldefs` (create a new file using `Right click on languages → New → File`):

```xml
<?xml version="1.0" encoding="UTF-8"?>

<language_definitions>
    <language processor="brainfuck"
              endian="little"
              size="16"
              variant="default"
              version="1.0"
              slafile="brainfuck.sla"
              processorspec="brainfuck.pspec"
              id="brainfuck:2:default">
        <description>brainfuck</description>
        <compiler name="default" spec="brainfuck.cspec" id="default"/>
    </language>
</language_definitions>
```

There is only one variant of the brainfuck processor, so we only specify one language. The `<language>` tag has a few required attributes that define the most important properties of the processor variant. One thing to note is the `processorspec` attribute. This attribute points to a `.pspec` file, which specifies the processor. The file specified in the `.sla` file is responsible for providing Ghidra information about the disassembly and decompilation of machine code. Optionally, you can add a processor manual using the `manualindexfile` attribute. This will be covered in a future post.

A `<language>` tag must contain a `<description>` subtag and at least one `<compiler>` subtag (but you may specify more). The `<compiler>` tag points to `brainfuck.cspec`, which describes specific information about the compiler. There are some other tags you can include, but they are not interesting for now.

# The processor specification
The structure of the `.pspec` file is described by [`processor_spec.rxg`][proc_spec_rxg]. There is no official documentation. The file can contain a bunch of tags describing the memory and registers of the processor, all of which are optional. We'll leave it empty for now:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<processor_spec>
</processor_spec>
```

# The compiler specification
The layout and purpose of the `.cspec` file is pretty well[^cspec_doc_note] documented in the _Compiler Specification_:

> The compiler specification is a required part of a Ghidra language module for supporting disassembly and analysis of a particular processor. Its purpose is to encode information about a target binary which is specific to the compiler that generated that binary. Within Ghidra, the SLEIGH specification allows the decoding of machine instructions for a particular processor, like Intel x86, but more than one compiler can produce those instructions. For a particular target binary, understanding details about the specific compiler used to build it is important to the reverse engineering process. The compiler specification fills this need, allowing concepts like parameter passing conventions and stack mechanisms to be formally described.

Only the `<default_proto>` tag is required. This tag describes the default calling convention (**prototype**) for this compiler. Brainfuck has branches, but it doesn't have calls, so there is not really a prototype. We create a prototype as minimal as possible:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<compiler_spec>
    <default_proto>
        <prototype name="default" extrapop="unknown" stackshift="0">
            <input></input>
            <output></output>
        </prototype>
    </default_proto>
</compiler_spec>
```

Be sure to read the _Compiler Specification_ and [`compiler_spec.rxg`][comp_spec_rxg] for more detailed information.

# The language specification
The `.sla` file describes the instruction set and makes it possible for Ghidra to disassemble and decompile binaries. The file contains information for both disassembly and decompilation. The file describes the translation from machine code to a textual representation of each instruction (mnemonic, operands, etc.) for the disassembly. It also describes how these instructions affect memory and registers using an intermediate language called **p-code**. P-code operations operate on **varnodes**, which are generalizations of memory. A CPU register is a varnode, as well as a word or halfword in memory. The information in the `.sla` is used by the decompiler to construct the decompiled code.

Writing `.sla` files manually is very tedious. (Just look at `6502.sla` if you don't believe me.) Luckily, you don't have to write `.sla` files by hand. You can write `.slaspec` files instead, which are much more pleasant to write and maintain. (Compare `6502.slaspec` to `6502.sla` in terms of readability.) These `.slaspec` files are automatically compiled to `.sla` files by Ghidra.

The `.slaspec` is very well documented by the *Ghidra Language Specification*, so I will not go too deeply into the syntax of the `.slaspec`. Refer to the official specification for that.

The very first step is to create `brainfuck.slaspec`. Ghidra will compile this to `brainfuck.sla`, which is referred to by the `.ldefs`.

`.slaspec` files always start by defining the endianness of the processor, usually followed by an instruction alignment definition. The brainfuck processor is little endian and instructions are aligned to a 1-byte boundary (which is equivalent to no alignment, but let's define it anyway):

```
define endian=little;
define alignment=1;
```

The next step is to define the address spaces:

```
define space register  type=register_space  size=2  wordsize=2;
define space ram       type=ram_space       size=2  wordsize=2;
define space rom       type=ram_space       size=2  wordsize=1  default;
```

This is rather straightforward, except for one thing: the *Ghidra Language Specification* mentions the space type `rom_space`, but this type does not exist[^rom_space]. Using this space type will result in a crash. That's why `rom` is defined as `ram_space`, instead of `rom_space`.

The `rom` space is marked as default. This causes the binary to be loaded into the `rom` space. Note that the registers get their own address space.

Then we define registers in the `register` space:

```
define register offset=0x00 size=2 [pc ptr];
```

We can refer to these defined address spaces and registers in the instructions, but before we can define those instructions, we have to define a token. A **token** specifies the layout of an instruction or a part of it (in variable length instructions). Instructions in the brainfuck instruction set are very simple. The four least significant bits ([0,3]) define the opcode of the instruction. The other bits are undefined. Our token looks like this:

```
define token instr(8)
    op = (0, 3)
;
```

We can now finally define the instructions themselves. For now we'll only define the translation from machine code to the textual representation (disassembly) and not their semantics (decompilation). This is very simple. The `>` instruction looks like this:

```
:> is op=0x0 {}
```

Let's break this down. The `:` indicates that this 'translation', more formally called a **constructor**, should be added to the root instruction table. All instructions live in this table. Subtables can be used for constructing more complex instructions, which you can read about in the *Ghidra Language Specification*.

After the `:` comes the textual representation, formally called the **display section**, of the instruction. For this instruction, this is simply `>`. If an instruction has operands they can also be specified here (`add op1, op2`, for example).

The display section is followed by the `is` keyword, which itself is followed by the **bit pattern section**. The bit pattern section is used to match the bits of the machine code to constructors. The bit pattern section for this instruction (and all other brainfuck instructions) consists of one constrain: the opcode is `0x0`. No more is needed to identify the `>` instruction.

Constructors end with a semantic section, surrounded by curly braces ({}). This section describes how instructions manipulate memory and registers. We leave it empty for now.

All constructors for compiled brainfuck instructions can be defined like this:

```
:> is op=0x0 {}
:< is op=0x1 {}

:+ is op=0x2 {}
:- is op=0x3 {}

:. is op=0x4 {}
:, is op=0x5 {}

:[ is op=0x6 {}
:] is op=0x7 {}
```

# Testing
This is all that's needed to describe the language, processor, compiler and instruction set. We can now test it. Hit the debug button in the toolbar to start a debugged instance of Ghidra with our module automatically loaded in.

![]({{page_images}}/debug_menu.png "Hit the bug-like icon to start a debugging session")

Create a new project in Ghidra and give it a name. Now we need a brainfuck file to disassemble. I'll be using the previously generated `mul2.bin`. Drag the binary into Ghidra and open it with the code browser. Ghidra asks if it should analyze the file. It doesn't matter whether you choose yes or no, because we haven't told Ghidra how to auto-analyze brainfuck binaries. Nothing will happen if you click yes.

The listing view shows the bytes of our binary. To disassemble them press `D` at the first address. The listing will now show the disassembly of our binary, which should look similar to this:

![]({{page_images}}/disassembly.png "Disassembly of our brainfuck binary")

Hooray! We've now created an overcomplicated brainfuck disassembler that could be replaced by a few lines of python code. This seems like a huge overkill, but in the [next post][next_post] we'll look at getting the decompiler to work so we can do some real reversing on brainfuck binaries. See you then!

You can find the final code for this post [here][final_code].

# Footnotes
[^lang_spec]: See `docs/languages/` in your Ghidra installation. Online (compiled) version available [here][compiled_lang_spec].
[^comp_spec]: See [`Ghidra/Features/Decompiler/​src/main/doc/`][comp_spec_src] in the source code. You'll have to compile these yourself (see [issue #472][issue_472]). Online (compiled) version available [here][compiled_comp_spec].
[^ghidra_git]: [NationalSecurityAgency/ghidra on GitHub, commit `eaf6ab2`][commit_eaf6ab2]. It may not be the latest commit by the time you're reading this.
[^lang_prov]: The [`SleighLanguageProvider`][sleigh_lang_prov] is responsible for [finding][sleigh_lang_prov_find] and [parsing][sleigh_lang_prov_parse] these `.ldefs` files.
[^cspec_doc_note]: AFAIK, the following valid tags are not documented in the _Compiler Specification_: `spacebase`, `deadcodedelay`, `segmentop`, `resolveprototype`, `eval_current_prototype` and `eval_called_prototype`. Some of them are documented elsewhere. There might be other subtle differences between the docs and the code.
[^rom_space]: See [`space_class.java`][space_class]. Only `register_space` and `ram_space` are defined.

[wiki_bf]: https://en.wikipedia.org/wiki/Brainfuck#Language_design
[bfc_src]: {{site.github_repo}}/tree/master{{page_assets}}/source/brainfuck/bfc.py
[mul_src]: {{site.github_repo}}/tree/master{{page_assets}}/source/brainfuck/mul2.bf
[eclipse]: https://www.eclipse.org/downloads/
[x86_ldefs]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Processors/x86/data/languages/x86.ldefs
[sleigh_highlight]: https://marketplace.visualstudio.com/items?itemName=CarloMaragno.sleighighlight
[wiki_relax_ng]: https://en.wikipedia.org/wiki/RELAX_NG
[lang_def_rxg]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/data/languages/language_definitions.rxg
[proc_spec_rxg]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/data/languages/processor_spec.rxg
[comp_spec_rxg]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/data/languages/compiler_spec.rxg
[next_post]: {{series_posts[1].url}}
[final_code]: {{site.github_repo}}/tree/master{{page_assets}}/source

[compiled_lang_spec]: {{page_assets}}/ghidra_docs/language_spec/index.html
[comp_spec_src]: https://github.com/NationalSecurityAgency/ghidra/tree/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Features/Decompiler/src/main/doc
[issue_472]: https://github.com/NationalSecurityAgency/ghidra/issues/472
[compiled_comp_spec]: {{page_assets}}/ghidra_docs/compiler_spec/index.html
[commit_eaf6ab2]: https://github.com/NationalSecurityAgency/ghidra/commit/eaf6ab250df63652cd455f0c051ce7e03f4f641b
[sleigh_lang_prov]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/app/plugin/processors/sleigh/SleighLanguageProvider.java#L40
[sleigh_lang_prov_find]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/app/plugin/processors/sleigh/SleighLanguageProvider.java#L73
[sleigh_lang_prov_parse]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/app/plugin/processors/sleigh/SleighLanguageProvider.java#L80
[space_class]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/pcodeCPort/slgh_compile/space_class.java#L21
