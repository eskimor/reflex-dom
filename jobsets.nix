{ prs }:

let
  pkgs = (import ./reflex-platform.nix {}).nixpkgs;
  mkFetchGithub = value: {
    inherit value;
    type = "git";
    emailresponsible = false;
  };
in
with pkgs.lib;
let
  defaults = jobs: {
    inherit (jobs) description;
    enabled = 1;
    hidden = false;
    keepnr = 10;
    schedulingshares = 100;
    checkinterval = 120;
    enableemail = false;
    emailoverride = "";
    nixexprinput = "reflex-dom";
    nixexprpath = "release.nix";
    inputs = jobs.inputs // {
      nixpkgs = {
        type = "git";
        value = "https://github.com/NixOS/nixpkgs-channels nixos-unstable";
        emailresponsible = false;
      };
      config = {
        type = "nix";
        value = "{ android_sdk.accept_license = true; }";
        emailresponsible = false;
      };
    };
  };
  branchJobset = branch: defaults {
    description = "reflex-dom-${branch}";
    inputs = {
      reflex-dom = {
        value = "https://github.com/reflex-frp/reflex-dom ${branch}";
        type = "git";
        emailresponsible = false;
      };
    };
  };
  makePr = num: info: {
    name = "reflex-dom-pr-${num}";
    value = defaults {
      description = "#${num}: ${info.title}";
      inputs = {
        reflex-dom = {
          #NOTE: This should really use "pull/${num}/merge"; however, GitHub's
          #status checks only operate on PR heads.  This creates a race
          #condition, which can currently only be solved by requiring PRs to be
          #up to date before they're merged.  See
          #https://github.com/isaacs/github/issues/1002
          value = "https://github.com/reflex-frp/reflex-dom pull/${num}/head";
          type = "git";
          emailresponsible = false;
        };
      };
    };
  };
  processedPrs = mapAttrs' makePr (builtins.fromJSON (builtins.readFile prs));
  jobsetsAttrs = processedPrs //
    genAttrs ["develop"] branchJobset;
in {
  jobsets = pkgs.writeText "spec.json" (builtins.toJSON jobsetsAttrs);
}
