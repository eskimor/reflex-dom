let
  reflex-platform-src = (import <nixpkgs> {}).fetchFromGitHub {
    owner = "reflex-frp";
    repo = "reflex-platform";
    rev = "384cd850f3adf1d404bced2424b5f6efb0f415f2";
    sha256 = "1ws77prqx8khmp8j6br1ij4k2v4dlgv170r9fmg0p1jivfbn8y9d";
  };
in import reflex-platform-src
