
# Design Principles

Here is a description of principles that guide design decisions.

## Language

Building a great shell is a huge effort and so is designing a great language and building a great runtime. Tackling all these at the same time is very hard. By building on top of well-designed language we narrow down the scope which improves the chance of success.

We are building on top of Clojure because of its great design and values, read about its [philosophy](http://www.drdobbs.com/architecture-and-design/the-clojure-philosophy/240150710) and [rationale](https://clojure.org/about/rationale). I would like to point out following values:

- simplicity
- expressiveness
- clarity

It is preferred to use existing Clojure features if they can be reasonably applied to keep the shell core small.

## Shell

A lot of inspiration comes from [fish shell](https://fishshell.com/docs/current/design.html), namely:
- user focus
- feature discoverability
- reasonable defaults and minimal configuration

When customization is necessary prefer extension points and function composition over introducing configuration options.

## General

The overall strategy is to get to feature completeness as soon as possible with reasonable trade-offs. That means leveraging existing libraries as much as possible and writing simple code even though it may not provide best performance. It is important not to fall into the premature optimization trap.

After the feature completeness we can experiment with innovative ideas and try out new paradigms. Once we learn what sticks and what doesn't we can shift focus more on performance and stability.

Great inspiration to keep in mind is also [suckless philosophy](https://suckless.org/philosophy). Its main values of software are being simple, minimal and usable.
