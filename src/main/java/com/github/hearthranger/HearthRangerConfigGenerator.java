package com.github.hearthranger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * HearthRangerConfigGenerator is a utility class that generates configuration files
 * for Hearthstone cards used in HearthRanger bot customization. It reads a deck file,
 * fetches card data from the Hearthstone JSON API, matches the cards with the deck,
 * and creates individual configuration files for each card.
 */
@Slf4j
public class HearthRangerConfigGenerator {

    private static final String OUTPUT_DIR = "hearthranger_configs";
    private static final String API_URL = "https://api.hearthstonejson.com/v1/latest/enUS/cards.json";

    public static void main(String[] args) {
        try {
            // Read deck from file
            List<String> deckCardNames = readDeckFromFile("deck.txt");

            // Fetch card data from Hearthstone API
            List<Card> allCards = fetchCards();

            // Match deck cards with API data
            List<Card> deckCards = matchDeckCards(allCards, deckCardNames);

            // Generate configuration files
            generateConfigs(deckCards);

            System.out.println("Configurations generated in directory: " + OUTPUT_DIR);
        } catch (Exception e) {
            log.error("Exception happened: ", e);
        }
    }
    /**
     * Fetches all card data either from a local file or from the Hearthstone JSON API.
     *
     * @return a list of all Hearthstone cards
     * @throws IOException if an I/O error occurs
     */
    private static List<Card> fetchCards() throws IOException {
        Path cachedFile = Paths.get("hs_cards.json");
        Gson gson = new Gson();

        // Check if file exists and is not empty
        if (Files.exists(cachedFile) && Files.size(cachedFile) > 0) {
            System.out.println("Reading card data from hs_cards.json...");
            try (BufferedReader reader = Files.newBufferedReader(cachedFile)) {
                Card[] cachedCards = gson.fromJson(reader, Card[].class);

                if (cachedCards != null && cachedCards.length > 0) {
                    System.out.println("Loaded " + cachedCards.length + " cards from hs_cards.json.");
                    return Arrays.asList(cachedCards);
                }
            } catch (Exception e) {
                System.err.println("Failed to read or parse hs_cards.json: " + e.getMessage());
            }
        }

        // If file doesn't exist or is empty, fetch from API
        System.out.println("Fetching card data from Hearthstone API...");
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader apiReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            Card[] apiCards = gson.fromJson(apiReader, Card[].class);

            if (apiCards != null && apiCards.length > 0) {
                System.out.println("Fetched " + apiCards.length + " cards from the API.");

                // Write the data to hs_cards.json for future runs
                try (BufferedWriter writer = Files.newBufferedWriter(cachedFile)) {
                    gson.toJson(apiCards, writer);
                    System.out.println("Card data cached in hs_cards.json.");
                }
                return Arrays.asList(apiCards);
            }
        }
        throw new IOException("Failed to fetch card data from API.");
    }
    /**
     * Reads a deck list from the specified file and extracts card names.
     *
     * @param fileName the name of the file containing the deck list
     * @return a list of card names from the deck
     * @throws IOException if an I/O error occurs
     */
    private static List<String> readDeckFromFile(String fileName) throws IOException {
        System.out.println("Reading deck from " + fileName + "...");
        List<String> deckCardNames = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || !line.startsWith("# 1x") && !line.startsWith("# 2x")) {
                    continue;
                }

                String[] parts = line.split("\\) ", 2);
                if (parts.length > 1) {
                    String cardName = parts[1].trim();
                    deckCardNames.add(cardName);
                }
            }
        }

        System.out.println("Read " + deckCardNames.size() + " cards from the deck.");
        return deckCardNames;
    }

    /**
     * Fetches all card data from the Hearthstone JSON API.
     *
     * @return a list of all Hearthstone cards
     * @throws IOException if an I/O error occurs
     */
    private static List<Card> fetchCardsFromApi() throws IOException {
        System.out.println("Fetching card data from Hearthstone API...");
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            Gson gson = new Gson();
            Card[] cards = gson.fromJson(reader, Card[].class);
            System.out.println("Fetched " + cards.length + " cards from the API.");
            return Arrays.asList(cards);
        }
    }

    /**
     * Matches the cards in the deck with the data retrieved from the API.
     *
     * @param allCards      a list of all Hearthstone cards from the API
     * @param deckCardNames a list of card names from the deck
     * @return a list of cards from the deck matched with their data
     */
    private static List<Card> matchDeckCards(List<Card> allCards, List<String> deckCardNames) {
        Map<String, Card> cardMap = new HashMap<>();
        for (Card card : allCards) {
            if (card.name != null) {
                cardMap.put(card.name.toLowerCase(), card);
            }
        }

        List<Card> deckCards = new ArrayList<>();
        for (String cardName : deckCardNames) {
            Card card = cardMap.get(cardName.toLowerCase());
            if (card != null) {
                deckCards.add(card);
            } else {
                System.err.println("Card not found in API data: " + cardName);
            }
        }

        System.out.println("Matched " + deckCards.size() + " cards with API data.");
        return deckCards;
    }

    /**
     * Generates configuration files for the specified deck cards.
     *
     * @param deckCards a list of cards to generate configurations for
     * @throws IOException if an I/O error occurs
     */
    private static void generateConfigs(List<Card> deckCards) throws IOException {
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectory(outputPath);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        for (Card card : deckCards) {
            Map<String, Object> config = new HashMap<>();
            config.put("GameCardId", card.id);
            config.put("ConfigComment", card.name + ": " + (card.flavor != null ? card.flavor : "Placeholder comment for this card."));
            config.put("Artist", card.artist);
            config.put("Attack", card.attack);
            config.put("CardClass", card.cardClass);
            config.put("Collectible", card.collectible);
            config.put("Cost", card.cost);
            config.put("DBFId", card.dbfId);
            config.put("Elite", card.elite);
            config.put("Flavor", card.flavor);
            config.put("Health", card.health);
            config.put("Rarity", card.rarity);
            config.put("Set", card.set);
            config.put("Text", card.text);
            config.put("Type", card.type);

            Map<String, Object> beforePlayCardBonus = new HashMap<>();
            List<Map<String, String>> values = new ArrayList<>();

            values.add(Map.of(
                    "comment", "Example: don't play this card if there is no weapon",
                    "condition", "my_heroweapon(count()) <= 0",
                    "value", "-10"
            ));

            beforePlayCardBonus.put("values", values);
            config.put("BeforePlayCardBonus", beforePlayCardBonus);

            Path filePath = outputPath.resolve(card.id + ".json");
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                gson.toJson(config, writer);
            }

            System.out.println("Generated config for " + card.name + " (" + card.id + ")");
        }
    }

    /**
     * Represents a Hearthstone card with all its properties.
     */
    private static class Card {
        String id;
        String name;
        String artist;
        Integer attack;
        String cardClass;
        Boolean collectible;
        Integer cost;
        Integer dbfId;
        Boolean elite;
        String flavor;
        Integer health;
        String rarity;
        String set;
        String text;
        String type;
    }
}
