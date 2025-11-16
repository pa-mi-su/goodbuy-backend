package app.goodbuy.adapters.core.ingredients.pubchem;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wrapper for the raw PubChem JSON tree.
 * Contains:
 * - root JSON node
 * - error flag
 * - message when an error occurs
 */
public class PubChemRaw {

    private final JsonNode root;
    private final boolean error;
    private final String message;

    public PubChemRaw(JsonNode root) {
        this.root = root;
        this.error = false;
        this.message = null;
    }

    private PubChemRaw(String message) {
        this.root = null;
        this.error = true;
        this.message = message;
    }

    public static PubChemRaw empty(String msg) {
        return new PubChemRaw(msg);
    }

    public boolean isError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public JsonNode getRoot() {
        return root;
    }

    public boolean isOk() {
        return !error && root != null;
    }
}
