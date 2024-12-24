(ns build
  (:require [clojure.string :as string]
            [clojure.tools.build.api :as b] 
    [babashka.fs :refer [copy-tree]] 
    [babashka.process :refer [shell]])) 

 (defn build-cljs [] (println "npx shadow-cljs release app...") (let [{:keys [exit], :as s} (shell "npx shadow-cljs release app")] (when-not (zero? exit) (throw (ex-info "could not compile cljs" s))) (copy-tree "target/classes/cljsbuild/public" "target/classes/public")))

(def lib 'dumch/manipula)
(def main-cls (string/join "." (filter some? [(namespace lib) (name lib) "core"])))
(def version (format "0.0.1-SNAPSHOT"))
(def target-dir "target")
(def class-dir (str target-dir "/" "classes"))
(def uber-file (format "%s/%s-standalone.jar" target-dir (name lib)))
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean
  "Delete the build target directory"
  [_]
  (println (str "Cleaning " target-dir))
  (b/delete {:path target-dir}))

(defn prep [_]
  (println "Writing Pom...")
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src/clj"]})
  (b/copy-dir {:src-dirs ["src/clj" "resources" "env/prod/resources" "env/prod/clj"]
               :target-dir class-dir}))

(defn uber [_]
  (println "Compiling Clojure...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src/clj" "resources" "env/prod/resources" "env/prod/clj"]
                  :class-dir class-dir}) 
  (build-cljs)
  (println "Making uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :main main-cls
           :basis basis}))


(defn replace-key [{the-key :key}]
  (let [code-path "src/clj/dumch/manipula/web/controllers/the_key.clj"
        new-code-lines (->> code-path
                            slurp
                            string/split-lines
                            (map (fn [line]
                                   (if (string/starts-with? line "(def ^:build-time private-key")
                                     (str "(def private-key \"" the-key "\")")
                                     line))))]
    (spit
     code-path
     (string/join "\n" new-code-lines)
     :append false)))

(defn all [args]
  (do (clean nil) (replace-key args) (prep nil) (uber nil)))

(comment

  (def private-key "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUpRZ0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQ1N3d2dna29BZ0VBQW9JQ0FRQ29zMHAwUU5tOWVNbjAKREVkR05ud05zelpyQlNjbGxUWnQzWFJXazdUMVRkRXAwR21vOWE0cnk1TnNkMDl4VjBDTEpkUk9NOUZRM0ZrTgpUZFp5R1laVjB3QXMrekFBWFlSc25jMWxENkV2UHlreDV3Ly9IN1FlREZmckNJeTVpTkF1aEZwN3ZpZmtlK0NxCjBCdzRBZEFCL2xrMk1vdHVNTTJiZG1kbXdvT2t2N1ExT3ZnZ0g0VHBCWFUxRGtpZS9hSklqVGY1N0VnUDZHZm0KdDg3VE5TVWxtbFdHbjZNSmtoM2tONllNeUdmWjhiS3FobEQ3b245QjNyVVNzMHYvZnEvb3h5Nm9CVFVKa2tlOApiODh3akMrQXU2MVpwRFBNSHlkVDRRWGJ0a3N3YVpYSW5PNW1idVZNVjNFVENpSHJGQnUvS2JtSzFQKzhKYXA0Clg2R3NwVjR6QUEzcU5tdlFqMUY2QU5oSC9XOVpNR2RKaWJLakd2MFN6K1hsR1B4eEZraTFaQlh1dDRsTlAyU08KdEsxODZ1SnF2UWs4dmxKYmRSRFgzSDJzUDR6VGdoS3ZyUTdDdzJmOXdtYUpjNkt5RC9wL2Y1WGNNZm9LSkNOcQo2ajloUENwRkM3ejFoa0NpSEVaaHpqT0ljSWZYQjNFbTFuR3EzWmhlMkx2cDdZOXFPcFV3QkNSbWp1dmtUdUZBCjhRWWRhTFhGaGY3Qmt2SUEyY0hyZjZMOUZ4QTBVVk94TmVGVEFOVTdRYTEyN0JMdkVDM3hNdm5lMzhhYkIyZ3IKMU5xU3dFOGQ3THE4a2ozNlQyblV0UmFTYjZoRGxYYXArTXFsV1ptdFRicjk3ckRFcklLVDRoblVNUWM2TmwvWgpvOW54Y2FoTk8xT1JydmIxWjFqeWtsNUZJaTE2RHdJREFRQUJBb0lDQUJFNE44M1Uvc1M5eXhOcm0yelp5K0RVCkhhWmY0TWFQeS8zNzRFK2tCUkVTNzlvdVNWS3pQU09BUkp5S015UTFEVVFHeVB4d2dwbVgzRWtrM0ZKS003R1gKOTRrZjNKNzA3THJ2Z3BNaHZNaW5VRXVsTTdkTlk2TzdpOC9VVUJUeUY1bmY3YUo2M3ZTT0JuazVBK0Jkb3Y5SgpHSThWS3JGRkx2K0MzdndZdDdvOXpTV3Z6cnJzckxMaVdUdmdxTjM5c3E1b0o4SU5jYStaLzkwSGZ3ZUZBYlVzCll6VjZIdml2bnZoNEM2MTdXVXBnUnVYc0R1RVlqWmZLSlQ1eTZidDdFWGFYdWpYeFRpQWJlOUlLc2FIWmRWSFYKRmxVemt3dnZWQ0xXTWJSakQ1cXg5RldGV3pvK2NJNVIwSnRxYWRybWN3eVl0RFV0RjZ0N1JzTEJ0QzE4dGJnaApndW1mcDlvRnZSU2s1SjVwUTZhT0hKY2JwWTRET0VwTXZXYXNMRkw5cyswckJlNW4zdWROeCtvOXNnWVpXR1BkCjNCaXhsVEpZSHNZZGkvRTdXNGtwL3FNbUNHT0xiTUg1b1FiWmJOeWt5amNmTjNSOTA1SkpmUC90OTJGNXRnaFoKSnlZbnBLa1c4ak5vTE1zQWRWUXJkTnFweDhWT0s2SmlYaHp6T200ZmROcDgvK25FdmJPU1dDVG10WVVQeE5rNwphdXM4TVlaaTF6TkFHU3k0WkdnbEdad21YbjhXVWhxckZqQ002clVSNTlGK3ZRb0E1cmFrbTdaUkV2TUxhcng4CmtqVjZHYkVFek50QllSMGtCNG9ENlk0Nmc1WGJFY1RMNlNONHZPSDQvR0E5dlNVWDM4alV3c3E4SEoxNFkrWVUKMU9Nb0ZTZ1pza2pMWFM5bGI5NHBBb0lCQVFEVzR3WWhvcDkvcTczUG1qMmpMOFFJenJyMXppUVBQazVOYTRidwo0QWF2QUxxSlFKQzlnWElXTFdsdDhrUWtnaENpbnU0ZCsyUE8wckZLM2RicHpOV3c5eFJSREttQlVEM0d6WHZyCjdxRGFzSTU2THB4dldsamJpaStKeFhuWlk2OG1KVzE4QjJncTRGWCs4SXBiNitvYnNqVE9CSW10V0hhTUYyTHUKRlh2MWFlY0xBOXg3OXRWRlFFVy9pb2dEY01XQVRYRERmNFRvUURJR09PQ3NLVWxQdk9wc1NLdjNsTkJEY1prcwpTSEFuSXZJY1FoeFJISVZjZGh6OUM2QWovbGJzZmZyTUh4R21IbVNDVURiOTQ0TmJWakgwWXRzZzNXenNVU2FWCmhCZVFJQ2x3ejdxdjEwaE5sTnVCNjNQSnJnK1oyMGYwQ210eXNKc2tFWWFVRGdLcEFvSUJBUURJK2hmSHArUVAKZ20vQVRQa0tVa1BJN3FKdUlsQktOU2JjRTdiNTNQV2Y4UVRKbTlBM2ppZDdONlJycTN0akVhVFFUeE9scE9lawpEcExIOXNud1FDSlg1Z3RGTjdYaGduS1JzRU9IaGVtSlh2NG1ZMkE5WTF0eHNEd1dET1drSHA3MW9HK3drUWFHClNycE5NblFjaEtKSkdoM1lmenBCd280eDgwRGxOWkdwNmJhZWgzajJRYmYwZFRZWVJEeUJSRVhmT2dHbGtpL1oKeTd1dXRQUHRMd2pNWGNQdjc2aVgwTEUxazM4dXZlekdyb1g4Q3dZZmxKMDBPTU5BTys3ZGlML1ZOeS9jbmMzVgp5WDUyc0NRdHVESnNZKzU1M0NIb3NPM1QyL0hNQVlvQkVQT2gyMm5GNG5zVkpGVHBPUWtib1plRUFRWDU1eFFjCnRkNjBzajRqbkVIM0FvSUJBUURVNXpHWCthdlZycTd0RllDa1Foc1VMdDhGMHl2ZS9uaE9OSThKOEt3dWo2WkIKZm5ycGgyc2xsZkN6UWlsSmtxUXd1dzVwSGoyUkdTY1hhaHdZb1IzSHE4V3hWNzVKcE5yUU1aN3A3dy9vSEszTgpXSWtkdW9IM1lqNGZYa3lQbWpoYXJ4SitwRWdNMHgvZzZ5bnFVUjh1T2E0ZndGYzRMRUdvSXpPZGVDUE16eFJlCjBZQ09RY3lrUkQzV3ZNWnR3am1zR1EzMFpFK21YSlF6bmY3Y0ZEdlNpUFlxT3daRzBtWlZyQ042d0hwK0RCMmMKNmROc2VibVlGbDQ2U0Y1dHl2ME1hdlVsY0ZMb0o0eWxvQndjQ0dLSkNDbU9YTE9IS0Z0VytFQW1PeTJ0V25BMAptVnY2Qkc1YWozVzhqeSsxVG9PZWlLZ05ucFUrbU1QZFJJLzErTHVoQW9JQkFHNG43ZnJJQWcraUxjQllNRFJCCkROaElQQTFqajJCdEs2UjZ4ZExFRW5rYzhNUFVQRmNHK29ybDM2QUlPTFAvU3JmR2IvMWRtbjFvWTNsb3doeUYKK296MUVQNWFYNzEvODIzNUQ3cHJZcXFodjJtcEZHbnhXSURDMk53NUszRStPMkJrRkhQUnVhTEh1TDl4UlFVUQppYWJKd3N3VVNBa2RLelVqb0ZGQmdGcUNPRTlCNzhJQ0dXTExEK0JUSGxxMzRoaE5RZlBQWVp6ZHR6dzJBSERqClU4NDRJcG9UWVBQOU5mUW9xUkFrbDIvNjNvTmNRM254eWd5Q2hEcGozelBiclZHZlV3TjRGd2J6enZZYzYyZUgKeTFOdm5wbDZWN2VqcW1keDZXRXBBc3c4ejF6SzEzblgvaUNEYm1yNmtReHRFSkFRdzRiSzZsSW1PVEFQZGxHcwpadGNDZ2dFQVJJUVowUG9lK1lNUjFLNmJiaG5GUzl4b3Btc0VLbWZpVW9xWTU2Q1J2YW5lYmJxTzVISUdIZUQxCmVRRXRtU1VUWHBhU2p3NkNrSTlsUFoyWVZmcDBzUjk4anhrOEsrSXRqQnZnejNJVUlwODN3K3BYMmtRU0RSVGsKbUNhNk9jbzRWSEJwdFZpTjZUdmNpMENqUlNBeTdBVjVwdXMwdDRlSDlVUGVsdzFaRi9WcFNDYWVhNS9nL21hOApKUUZhUTNkQkxwMEhvZUswdUcweDFCb2ZuczdmR0VveXMvUkJXWlBTUVNEOHRwOHpQbkl4ZkxpdGxzUmh5Mm10ClorRytUVG5aZnViNkgrb05oVjI2OWFVMHJzM3ljblZYM3J2ZDZlQjJZRmFVT3hWUE81ZXZJNTQ1NnBoWVU2Qk0KN3F6cUIwMUMyaU1oUHZldXBPdVRwMGFzNG41aitRPT0KLS0tLS1FTkQgUFJJVkFURSBLRVktLS0tLQo="))
