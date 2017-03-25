[![Build Status](https://travis-ci.org/alainsahli/confluence-publisher.svg?branch=master)](https://travis-ci.org/alainsahli/confluence-publisher)
[![Coverage Status](https://coveralls.io/repos/github/alainsahli/confluence-publisher/badge.svg?branch=master)](https://coveralls.io/github/alainsahli/confluence-publisher?branch=master)
# Confluence Publisher

## Open points:
* Add some reporting service to print out result
* External links
* Internal cross references
* Links to attachments
* Rich content in tables
* Bullet and numbered lists

## Supported Asciidoc Elements

* Document title 
```
= Document Title
```
* Section levels
```
== Section level 1 (Heading 1 in Confluence)
=== Section level 2 (Heading 2 in Confluence)
==== Section level 3 (Heading 3 in Confluence)
===== Section level 4 (Heading 4 in Confluence)
====== Section level 5 (Heading 5 in Confluence)
```
* Paragraphs
```
Paragraph 1

Paragraph 2
```
* Listings
```
----
import java.util.List;
----
```
* Source listings
```
[source]
----
import java.util.List;
----

[source,java]
----
import java.util.List;
----
```
* Images
```
image::sunset.jpg[]
image::sunset.jpg[height="100",width="200",link="http://website.com"]
```
* Bold text
```
*bold*
```
* Italic text
```
_italic_
```
* Tables (with simple text content only)
```
[cols="3*", options="header"]
|===
| A
| B
| C

| 10
| 20
| 30

| Green
| Blue
| Red
|===
```
* Admonitions (NOTE, TIP, CAUTION, WARNING and IMPORTANT) with and without title
```
[WARNING]
.Warning Title
====
Some warning.
====
```
* Inter-Document Cross References
```
<<relative/path/to/target-page.adoc#,Label>>
```
* Includes (included pages need to use `_` prefix in file name)
```
include::relative/path/to/_included-page.adoc[]
```

* PlantUML Diagrams (requires dot to be installed)
embedded
```
[plantuml, embedded-diagram, png]
....
(foo) -> (bar) : test
....
```

included
```
plantuml::diagram.puml[]
```