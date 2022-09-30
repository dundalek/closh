class CloshZero < Formula
  desc "Bash-like shell based on Clojure"
  homepage "https://github.com/dundalek/closh"
  # url "https://example.com/foo-0.1.tar.gz"
  sha256 "cbae0fa155c88745de2cf8617ea70e2bed9c725a" # FIXME Can we have a reference to latest?
  head "https://github.com/dundalek/closh.git", :branch => "master"

  # https://formulae.brew.sh/formula/boot-clj
  depends_on "boot-clj" #, java too
  depends_on :java => "1.8+"
  
  bottle :unneeded # no idea, really. See https://docs.brew.sh/Bottles

  def install
    system "boot", "uberjar"
    
    jar_filename = "project.jar"    

    prefix.install "target/#{jar_filename}"
    bin.write_jar_script "#{prefix}/#{jar_filename}", "closh-zero-jvm"

  end
  
  # REVIEW I don't think the below does much
  test do
    jar_filename = "project.jar"    
    
    system "java", "-cp" "#{prefix}/#{jar_filename}", "clojure.main", "-e", "(System/exit 0)"
  end
end