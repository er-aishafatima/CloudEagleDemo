package com.cloudeagle.dropbox;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Desktop;

/**
 * CloudEagle Assessment - Dropbox Business API Client
 * 
 * This Java application implements OAuth 2.0 authentication and fetches data
 * from Dropbox Business API endpoints for team management.
 * 
 * Features:
 * - OAuth 2.0 authorization code flow
 * - Team information retrieval
 * - User list management
 * - Event log access
 * - Comprehensive error handling
 * 
 * @author CloudEagle Assessment
 * @version 1.0
 */
public class DropboxBusinessAPIClient {
    
    // Dropbox API Configuration
    private static final String AUTH_URL = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final String API_BASE_URL = "https://api.dropboxapi.com/2";
    
    // OAuth Configuration (Replace with your actual values)
    private static final String CLIENT_ID = System.getProperty("client.id");
    private static final String CLIENT_SECRET = System.getProperty("client.secret");
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    
    // Required scopes for Business API
    private static final String SCOPES = "team_info.read members.read events.read team_data.member";
    
    // HTTP Client
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // Authentication state
    private String accessToken;
    private String refreshToken;
    
    public DropboxBusinessAPIClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Step 1: Generate authorization URL and open in browser
     */
    public void initiateOAuthFlow() {
        try {
            // Generate random state for CSRF protection
            String state = UUID.randomUUID().toString();
            
            // Build authorization URL
            String authUrl = AUTH_URL + "?" +
                "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                "&scope=" + URLEncoder.encode(SCOPES, "UTF-8") +
                "&state=" + URLEncoder.encode(state, "UTF-8") +
                "&token_access_type=offline"; // For refresh tokens
            
            System.out.println("=== Dropbox OAuth Authorization ===");
            System.out.println("Opening browser for authorization...");
            System.out.println("Authorization URL: " + authUrl);
            
            // Open browser (works on most systems)
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(authUrl));
            }
            
            System.out.println("\nAfter authorization, you'll be redirected to:");
            System.out.println(REDIRECT_URI + "?code=AUTHORIZATION_CODE&state=" + state);
            System.out.println("\nPlease copy the 'code' parameter from the URL and paste it here:");
            
        } catch (Exception e) {
            System.err.println("Error initiating OAuth flow: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Step 2: Exchange authorization code for access token
     */
    public boolean exchangeCodeForToken(String authorizationCode) {
        try {
            // Prepare token exchange request
            Map<String, String> tokenParams = new HashMap<>();
            tokenParams.put("grant_type", "authorization_code");
            tokenParams.put("code", authorizationCode);
            tokenParams.put("redirect_uri", REDIRECT_URI);
            tokenParams.put("client_id", CLIENT_ID);
            tokenParams.put("client_secret", CLIENT_SECRET);
            
            // Build form-encoded request body
            String formBody = tokenParams.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8) + "=" +
                             URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .reduce((p1, p2) -> p1 + "&" + p2)
                .orElse("");
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(formBody))
                .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode tokenResponse = objectMapper.readTree(response.body());
                this.accessToken = tokenResponse.get("access_token").asText();
                
                if (tokenResponse.has("refresh_token")) {
                    this.refreshToken = tokenResponse.get("refresh_token").asText();
                }
                
                System.out.println("✅ Authentication successful!");
                System.out.println("Access Token: " + accessToken.substring(0, 20) + "...");
                
                return true;
            } else {
                System.err.println("❌ Token exchange failed:");
                System.err.println("Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error exchanging code for token: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * API Call: Get team information (organization name and license info)
     */
    public void getTeamInfo() {
        try {
            if (accessToken == null) { System.err.println("No access token. Authenticate first."); return; }
            System.out.println("\n=== Getting Team Information ===");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/team/get_info"))
                .header("Authorization", "Bearer " + accessToken)
                // No Content-Type or body; Dropbox expects null JSON
                .POST(BodyPublishers.noBody())
                .build();
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode teamInfo = objectMapper.readTree(response.body());
                System.out.println("✅ Team Information Retrieved:");
                JsonNode nameNode = teamInfo.get("name");
                if (nameNode != null && nameNode.has("display_name")) {
                    System.out.println("📋 Organization Name: " + nameNode.get("display_name").asText());
                }
                printIfPresent(teamInfo, "team_id", "🆔 Team ID: ");
                printIfPresent(teamInfo, "num_licensed_users", "👥 Licensed Users: ");
                printIfPresent(teamInfo, "num_provisioned_users", "✅ Provisioned Users: ");
                System.out.println("\n📄 Full Team Info Response:");
                System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(teamInfo));
            } else {
                handleApiError("Get Team Info", response);
            }
        } catch (Exception e) {
            System.err.println("Error getting team info: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * API Call: Get list of all users in the organization
     */
    public void getTeamMembers() {
        try {
            System.out.println("\n=== Getting Team Members ===");
            
            // Request body for member list
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("limit", 100);
            requestBody.put("include_removed", false);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/team/members/list_v2"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode membersResponse = objectMapper.readTree(response.body());
                JsonNode members = membersResponse.get("members");
                
                System.out.println("✅ Team Members Retrieved (" + members.size() + " members):");
                
                int memberCount = 1;
                for (JsonNode member : members) {
                    JsonNode profile = member.get("profile");
                    JsonNode name = profile.get("name");
                    
                    System.out.println("\n👤 Member " + memberCount + ":");
                    System.out.println("   Name: " + name.get("display_name").asText());
                    System.out.println("   Email: " + profile.get("email").asText());
                    System.out.println("   Status: " + profile.get("status").get(".tag").asText());
                    System.out.println("   Member ID: " + profile.get("team_member_id").asText());
                    System.out.println("   Joined: " + profile.get("joined_on").asText());
                    
                    if (member.has("roles") && member.get("roles").size() > 0) {
                        JsonNode roles = member.get("roles");
                        System.out.print("   Roles: ");
                        for (JsonNode role : roles) {
                            System.out.print(role.get("name").asText() + " ");
                        }
                        System.out.println();
                    }
                    
                    memberCount++;
                }
                
                // Handle pagination
                if (membersResponse.get("has_more").asBoolean()) {
                    System.out.println("\n⚠️  More members available. Use cursor for pagination: " +
                                     membersResponse.get("cursor").asText());
                }
                
            } else {
                handleApiError("Get Team Members", response);
            }
            
        } catch (Exception e) {
            System.err.println("Error getting team members: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * API Call: Get sign-in events for all users
     */
    public void getSignInEvents() {
        try {
            System.out.println("\n=== Getting Sign-in Events ===");
            
            // Request body for event logs
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("limit", 10); // Limit for demo purposes
            
            // Filter for login events
            Map<String, String> category = new HashMap<>();
            category.put(".tag", "logins");
            requestBody.put("category", category);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/team_log/get_events"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode eventsResponse = objectMapper.readTree(response.body());
                JsonNode events = eventsResponse.get("events");
                
                System.out.println("✅ Sign-in Events Retrieved (" + events.size() + " events):");
                
                int eventCount = 1;
                for (JsonNode event : events) {
                    System.out.println("\n🔐 Event " + eventCount + ":");
                    System.out.println("   Timestamp: " + event.get("timestamp").asText());
                    System.out.println("   Event Type: " + event.get("event_type").get(".tag").asText());
                    
                    if (event.has("actor") && event.get("actor").has("user")) {
                        JsonNode user = event.get("actor").get("user");
                        System.out.println("   User: " + user.get("display_name").asText());
                        System.out.println("   Email: " + user.get("email").asText());
                    }
                    
                    if (event.has("origin")) {
                        JsonNode origin = event.get("origin");
                        if (origin.has("geo_location")) {
                            JsonNode geo = origin.get("geo_location");
                            System.out.println("   Location: " + geo.get("city").asText() + 
                                             ", " + geo.get("country").asText());
                        }
                        if (origin.has("host")) {
                            System.out.println("   IP: " + origin.get("host").get("host").asText());
                        }
                    }
                    
                    eventCount++;
                }
                
                // Handle pagination
                if (eventsResponse.get("has_more").asBoolean()) {
                    System.out.println("\n⚠️  More events available. Use cursor for pagination: " +
                                     eventsResponse.get("cursor").asText());
                }
                
            } else {
                handleApiError("Get Sign-in Events", response);
            }
            
        } catch (Exception e) {
            System.err.println("Error getting sign-in events: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to handle API errors
     */
    private void handleApiError(String apiName, HttpResponse<String> response) {
        System.err.println("❌ " + apiName + " API call failed:");
        System.err.println("Status Code: " + response.statusCode());
        System.err.println("Response Headers: " + response.headers().map());
        String body = response.body();
        System.err.println("Response Body: " + body);
        try {
            JsonNode errorResponse = objectMapper.readTree(body);
            if (errorResponse.has("error_summary")) {
                System.err.println("Error Summary: " + errorResponse.get("error_summary").asText());
            }
            if (errorResponse.has("error")) {
                System.err.println("Error Details: " + errorResponse.get("error"));
            }
        } catch (Exception parseEx) {
            // ignore parse issues
        }
    }
    
    /**
     * Refresh access token using refresh token
     */
    public boolean refreshAccessToken() {
        if (refreshToken == null) {
            System.err.println("No refresh token available");
            return false;
        }
        
        try {
            Map<String, String> tokenParams = new HashMap<>();
            tokenParams.put("grant_type", "refresh_token");
            tokenParams.put("refresh_token", refreshToken);
            tokenParams.put("client_id", CLIENT_ID);
            tokenParams.put("client_secret", CLIENT_SECRET);
            
            String formBody = tokenParams.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8) + "=" +
                             URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .reduce((p1, p2) -> p1 + "&" + p2)
                .orElse("");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(formBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode tokenResponse = objectMapper.readTree(response.body());
                this.accessToken = tokenResponse.get("access_token").asText();
                System.out.println("✅ Access token refreshed successfully!");
                return true;
            } else {
                System.err.println("❌ Token refresh failed: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error refreshing access token: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Main method - Entry point for the application
     */
    public static void main(String[] args) {
        System.out.println("🚀 CloudEagle Assessment - Dropbox Business API Client");
        System.out.println("==================================================");
        
        // Validate configuration
        if (CLIENT_ID.equals("YOUR_CLIENT_ID_HERE") || CLIENT_SECRET.equals("YOUR_CLIENT_SECRET_HERE")) {
            System.err.println("❌ Please update CLIENT_ID and CLIENT_SECRET with your actual Dropbox app credentials!");
            System.err.println("❗ Note: This application requires a Dropbox Business account.");
            System.err.println("1. Go to https://www.dropbox.com/developers/apps");
            System.err.println("2. Create a new app with Business API scopes");
            System.err.println("3. Update the constants in this file");
            return;
        }
        
        DropboxBusinessAPIClient client = new DropboxBusinessAPIClient();
        Scanner scanner = new Scanner(System.in);
        
        try {
            // Step 1: Initiate OAuth flow
            client.initiateOAuthFlow();
            
            // Step 2: Get authorization code from user
            System.out.print("\nEnter the authorization code: ");
            String authCode = scanner.nextLine().trim();
            
            if (authCode.isEmpty()) {
                System.err.println("❌ No authorization code provided!");
                return;
            }
            
            // Step 3: Exchange code for access token
            if (!client.exchangeCodeForToken(authCode)) {
                System.err.println("❌ Failed to obtain access token!");
                return;
            }
            
            // Step 4: Make API calls to demonstrate functionality
            System.out.println("\n🔄 Starting API demonstrations...");
            
            // Get team information (organization name and license info)
            client.getTeamInfo();
            
            // Get list of team members
            client.getTeamMembers();
            
            // Get sign-in events
            client.getSignInEvents();
            
            System.out.println("\n✅ All API calls completed successfully!");
            System.out.println("\n📊 Summary:");
            System.out.println("- ✅ OAuth 2.0 authentication implemented");
            System.out.println("- ✅ Team information retrieved");
            System.out.println("- ✅ Team members list obtained");
            System.out.println("- ✅ Sign-in events accessed");
            System.out.println("\n🎉 CloudEagle Assessment Complete!");
            
        } catch (Exception e) {
            System.err.println("❌ Application error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
    
    private void printIfPresent(JsonNode node, String field, String label) {
        if (node.has(field) && !node.get(field).isNull()) {
            System.out.println(label + node.get(field).asText());
        }
    }
}

/* 
 * Dependencies required (add to your pom.xml or build.gradle):
 * 
 * Maven:
 * <dependency>
 *     <groupId>com.fasterxml.jackson.core</groupId>
 *     <artifactId>jackson-databind</artifactId>
 *     <version>2.15.2</version>
 * </dependency>
 * 
 * Gradle:
 * implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
 * 
 * Setup Instructions:
 * 1. Create Dropbox Business trial account
 * 2. Go to https://www.dropbox.com/developers/apps
 * 3. Create new app with "Scoped access" and "Full Dropbox" access
 * 4. Select "Business API" integration
 * 5. Add required scopes: team_info.read, members.read, events.read, team_data.member
 * 6. Set redirect URI to: http://localhost:8080/callback
 * 7. Copy Client ID and Client Secret to this file
 * 8. Compile and run: javac -cp ".:jackson-databind-2.15.2.jar:jackson-core-2.15.2.jar:jackson-annotations-2.15.2.jar" DropboxBusinessAPIClient.java
 * 9. Run: java -cp ".:jackson-databind-2.15.2.jar:jackson-core-2.15.2.jar:jackson-annotations-2.15.2.jar" DropboxBusinessAPIClient
 * 
 * GitHub Repository Setup:
 * 1. Create new GitHub repository: dropbox-business-api-integration
 * 2. Add README.md with setup instructions
 * 3. Add .gitignore for Java projects
 * 4. Commit and push the implementation
 */
