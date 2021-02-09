with import <nixpkgs> {};
stdenv.mkDerivation {
  name = "closh-env";
  buildInputs = [
    bash
    clojure
    git
    graalvm11-ce
    which
  ];
}

