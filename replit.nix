{ pkgs }: {
    deps = [
      pkgs.nodejs-16_x
        pkgs.clojure
        pkgs.clojure-lsp
    ];
}