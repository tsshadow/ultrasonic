package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"sort"
	"strings"
	"time"
)

type VersionInfo struct {
	APK         string    `json:"apk"`
	VersionName string    `json:"versionName"`
	VersionCode string    `json:"versionCode"`
	BuildDate   time.Time `json:"buildDate"`
	Filename    string    `json:"filename"`
	URL         string    `json:"url"`
}

var apkRegex = regexp.MustCompile(`^(.+)-v(.+)-(\d+)(?:-unsigned)?\.apk$`)

func getLatestVersion(apkName string, r *http.Request) (*VersionInfo, error) {
	files, err := os.ReadDir("dist")
	if err != nil {
		return nil, err
	}

	var versions []VersionInfo
	for _, f := range files {
		if f.IsDir() || !strings.HasSuffix(f.Name(), ".apk") {
			continue
		}

		matches := apkRegex.FindStringSubmatch(f.Name())
		if len(matches) == 4 {
			name := matches[1]
			if apkName != "" && name != apkName {
				continue
			}

			info, _ := f.Info()
			scheme := "http"
			if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" {
				scheme = "https"
			}
			
			versions = append(versions, VersionInfo{
				APK:         name,
				VersionName: matches[2],
				VersionCode: matches[3],
				BuildDate:   info.ModTime(),
				Filename:    f.Name(),
				URL:         fmt.Sprintf("%s://%s/%s", scheme, r.Host, f.Name()),
			})
		}
	}

	if len(versions) == 0 {
		return nil, fmt.Errorf("no versions found for %s", apkName)
	}

	// Sort by BuildDate descending
	sort.Slice(versions, func(i, j int) bool {
		return versions[i].BuildDate.After(versions[j].BuildDate)
	})

	return &versions[0], nil
}

func main() {
	distDir := "dist"
	if _, err := os.Stat(distDir); os.IsNotExist(err) {
		fmt.Println("Warning: dist directory does not exist, creating it.")
		os.Mkdir(distDir, 0755)
	}

	// Health check
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	})

	// Version API
	versionHandler := func(w http.ResponseWriter, r *http.Request) {
		apkName := r.URL.Query().Get("apk")
		if apkName == "" {
			apkName = "ultrasonic" // Default
		}
		version, err := getLatestVersion(apkName, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Access-Control-Allow-Origin", "*")
		json.NewEncoder(w).Encode(version)
	}

	http.HandleFunc("/api/version", versionHandler)
	http.HandleFunc("/get", versionHandler)

	// Static files (APKs and index.html)
	fs := http.FileServer(http.Dir(distDir))
	http.Handle("/", fs)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8275" // Nice port referencing APK (275 on phone keypad)
	}

	fmt.Printf("Starting apk-hoster on port %s...\n", port)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}
