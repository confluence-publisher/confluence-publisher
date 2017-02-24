[![Build Status](https://travis-ci.org/alainsahli/confluence-publisher.svg?branch=master)](https://travis-ci.org/alainsahli/confluence-publisher)
[![Coverage Status](https://coveralls.io/repos/github/alainsahli/confluence-publisher/badge.svg?branch=master)](https://coveralls.io/github/alainsahli/confluence-publisher?branch=master)
# Confluence Publisher

## Open points:
* Add some reporting service to print out result
* Page content comparison is not working and therefore new versions are always created
* Link between pages
* Rich content in tables
* Info/error/warning blocks
* Bullet and numbered lists

## Supported Asciidoc Elements

* Document title 
```
:title: Document Title
```
* Section levels
```
= Title level 0
== Title level 1
=== Title level 2
==== Title level 3
===== Title level 4
====== Title level 5
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
cols="3*", options="header"]
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
```
