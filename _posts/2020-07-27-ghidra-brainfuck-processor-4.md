---
title: "Implementing a brainfuck CPU in Ghidra - part 4: Renaming the analyzer and adding a manual"
tags: Ghidra
series: ghidra_brainfuck
series_part: p0004
---

{%-include utils.html-%}
{%-include series.html-%}

Now that our processor module can finally disassemble and decompile brainfuck binaries, we can make some slight improvements to the module. We'll do two things in this blogpost: first we'll rename the analyzer and then we add a processor manual

# Renaming the analyzer
Right now, the analyzer that resolves branch destinations is called `BrainfuckAnalyzer.java`. This name doesn't really make it clear what it does. Maybe `BranchDestinationResolver.java` would be a better name, as it's more descriptive.

This sounds easy, right? Just right click `BrainfuckAnalyzer.java` and rename it to `BrainfuckAnalyzer.java`. Don't forget to also rename the class and constructor to `BranchDestinationResolver`. But, it's not *that* easy or I wouldn't have devoted half a blog post to it :)


If we now restart Ghidra, the analyzer has disappeared from analysis window. There's no `BranchDestinationResolver` or `BrainfuckAnalyzer` in the list. What happened?

![Analysis window]({{page_images}}/analysis_options.png "It's gone :(")

As it turns out, Ghidra only shows analyzers that are valid extension points. An **extension point** is a class that extends the functionality of Ghidra. There are two requirements for a class to be an extension point:

1. It must (directly or indirectly) derive from the `ExtensionPoint` class.
2. The containing file must have a valid extenstion point suffix.

The first requirement is met. The `BranchDestinationResolver` class indirectly extends `ExtensionPoint`[^deriv]. The problem is with the filename. By default only certain suffixes (there are about fifty) are recognized as extension point suffixes, the `Analyzer` suffix among them. `Resolver` is not a valid extension point suffix, so Ghidra doesn't recognize `BranchDestinationResolver` as an analyzer, while `BrainfuckAnalyzer` is fine because it ends with `Analyzer`.

We could append a valid suffix to the analyzer name to ensure Ghidra recognizes it, but it would result in an akward name (e.g. `BranchDestinationResolverAnalyzer` or `BranchDestinationAddressCorrelator`). We could also drop the `Resolver` suffix and use the `Analyzer` suffix instead (`BranchDestinationAnalyzer` sounds better). Instead of doing this, we can also register a new extension point suffix.

To register a new extension point suffix, we create a file in the `data` directory of the project called `ExtensionPoint.manifest`. It contains only one line:

```
Resolver
```

That's all. Ghidra recognizes the `ExtensionPoint.manifest` file and registers all suffixes in the manifest (separated by newlines). This is also how the default extension points suffixes are registered. For example, the `Analyzer` suffix is registered in `Ghidra/Features/Base/data/ExtensionPoint.manifest`.

If we now start Ghidra and open the analysis window, the analyzer shows up again:

![Analysis window]({{page_images}}/analysis_options_resolver.png "It's back again :)")

This was a very brief look at extension points. The whole extension point mechanism deserves a post of its own. If you're interested in how it works, I suggest looking at the [`ClassSearcher`][class_searcher], and [`ClassFinder`][class_finder] classes.

# Adding a manual
Now something different: adding a manual to the processor module. As said in the first post, a language in the `.ldefs` file can have a `manualindexfile` attribute pointing to a processor manual index file. A **processor manual** consists of one or more PDF files that contain documentation for the instruction set. A **processor manual index** maps instruction mnemonics to their corresponding page in the manual.

The manual index is documented at [`GhidraDocs/languages/manual_index.txt`][mandoc], but the documentation is not fully complete. The code that loads the manual index file can be found [here][load_idx].

Suppose we've got a dummy manual, [`bfman.pdf`][bfman]. It's manual index file would look like this:

`data/manuals/brainfuck.idx`:
```
@ bfman.pdf [Brainfuck Manual]

>, 1
# the '<' instruction is intentionally omitted

+, 2
-, 2

,, 3
., 3

[, 4
], 4
```

The first line starting with `@` is called a file switch. It sets the current manual file. The `[Brainfuck Manual]` part provides a description for this manual. It's optional and is shown when Ghidra can't find the manual file, so the user can locate the manual elsewhere. There can be multiple file switches in a manual index. This is useful when the processor manual spans over several volumes.

As stated in the index file, the `<` instruction is omitted. This is because the `<` character has a special (undocumented) meaning. It can be used to import another index file. There's no way to escape this character, which makes it impossible to create an entry for the `<` instruction. The only way to get around this is to rename the `<` instruction. For now, we'll just omit the `<` instruction.

Also good to know (and again undocumented): a `#` indicates a comment.

The only thing left to do now is to add the manual index file to the language definition in `brainfuck.ldefs`. This is done by adding the following attribute to the language tag: `manualindexfile="../manuals/brainfuck.idx"`.

That's all there is to manual index files. If you now right-click an instruction and click `Processor Manual...`, Ghidra will show the documentation for that instruction!

![Instruction context menu]({{page_images}}/processor_manual.png "Click Processor Manual to open the docs")

In this post, we've improved the usability of the processor module. Meanwhile, the module still produces poor decompilation output. Next time, we'll look at improving the decompilation of our module. Hopefully, we'll manage to produce better decompilations.

# Footnotes
[^deriv]: `BranchDestinationResolver ⊂ AbstractAnalyzer ⊂ Analyzer ⊂ ExtensionPoint`

[class_searcher]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/Generic/src/main/java/ghidra/util/classfinder/ClassSearcher.java
[class_finder]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/Generic/src/main/java/ghidra/util/classfinder/ClassFinder.java
[mandoc]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/GhidraDocs/languages/manual_index.txt
[bfman]: {{page_assets}}/bfman.pdf
[load_idx]: https://github.com/NationalSecurityAgency/ghidra/blob/eaf6ab250df63652cd455f0c051ce7e03f4f641b/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/app/plugin/processors/sleigh/SleighLanguage.java#L1264