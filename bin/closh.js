#!/usr/bin/env node
'use strict';
var spawn = require('child_process').spawn;
var path = require('path');

var bin = 'lumo';
var args = [
  '--classpath', path.join(__dirname, '../src'),
  '--cache', path.join(process.env.HOME, '.lumo_cache'),
  path.join(__dirname, '../src/closh/main.cljs')
];

spawn(bin, args, { stdio: 'inherit' }).on('exit', process.exit);
