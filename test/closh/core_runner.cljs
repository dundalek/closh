(ns closh.core-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [closh.core-test]))

(doo-tests 'closh.core-test)
