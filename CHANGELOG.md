# Changelog

## [0.2.2](https://github.com/dundalek/closh/compare/v0.2.1...v0.2.2) (2018-07-30)

- Bump up deasync dependency
- Upgrade lumo to 1.9.0-alpha which should fix installation issues

## [0.2.1](https://github.com/dundalek/closh/compare/v0.2.0...v0.2.1) (2018-07-04)

- Add node version check to the install script to prevent people from running into issues
- Add a logo
- Internal code cleanups

## [0.2.0](https://github.com/dundalek/closh/compare/v0.1.0...v0.2.0) (2018-04-16)

### New features

- Environment variable integration ([#16](https://github.com/dundalek/closh/issues/16))
- Persistent history ([#23](https://github.com/dundalek/closh/pull/23))
- Tab-completion ([#6](https://github.com/dundalek/closh/issues/6))
- Improved reader ([#39](https://github.com/dundalek/closh/issues/39))
- Signal control ([#30](https://github.com/dundalek/closh/issues/30))
- Aliases ([#12](https://github.com/dundalek/closh/issues/12))

### Bugfixes

- Many

### Changes

- Upgrade Lumo to 1.8.0 and install it as local dependency

## 0.1.0 (2017-10-31)

Initial version with following features:

- Command execution
- Pipes
- IO Redirects
- Interactive mode REPL
- Dynamic prompt
- Load `~/.closhrc` on startup
