package uz.payflow.account;

/** What the sender sees before confirming a transfer: whose account it is, without the full name. */
public record RecipientResponse(String number, String ownerName, Currency currency) {
}
