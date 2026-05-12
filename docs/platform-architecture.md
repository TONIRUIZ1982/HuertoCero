# HuertoCero global platform architecture

## Product thesis

HuertoCero should win on proximity, freshness and trust instead of price. The marketplace loop is:

1. Buyer opens the app and sees nearby supply.
2. The map and product feed surface scarce, fresh inventory.
3. Buyer reserves a partial quantity.
4. Seller confirms pickup or delivery in chat.
5. Notifications bring both sides back.

More sellers create more local inventory, which attracts more buyers, which improves seller liquidity and strengthens the network.

## Users

HuertoCero supports three account types:

- `farmer`: producers with harvest-based inventory, freshness signals and farm reputation.
- `shop`: stores with recurring catalogues, subscriptions, featured products and team workflows.
- `individual`: private sellers with lighter profiles and stricter trust limits.

All accounts should keep `ratingAverage`, `ratingCount`, `reservationCount`, `cancelRate`, `responseTimeMinutes`, `verificationStatus` and `createdAt`.

## Firestore model

Core collections:

- `users/{userId}`: profile, account type, reputation, locale, preferred currency, notification settings.
- `products/{productId}`: product card, seller, geohash, price, currency, unit, `stockTotal`, `stockReserved`, media and category.
- `reservas/{reservationId}`: buyer, seller, product, quantity, unit, price snapshot, status, expiration and monetization fields.
- `conversations/{conversationId}` and `messages/{messageId}`: buyer-seller negotiation.
- `reviews/{reviewId}`: seller reputation after fulfilled reservations.
- `reports/{reportId}`: moderation queue.
- `boosts/{boostId}`: featured placement campaigns.
- `recommendationEvents/{eventId}`: views, favorites, shares and reservations for ranking.

Global fields:

- `currency`: ISO 4217 code such as `EUR`, `USD`, `JPY`, `INR`.
- `unit`: normalized unit such as `kg`, `g`, `lb`, `unit`, `box`, `bunch`, `L`.
- `locale`: BCP 47 language tag such as `es`, `en`, `fr`, `ar`.
- `geohash`: indexed geospatial prefix for fast nearby queries.

## Reservation logic

Reservations must always run in a Firestore transaction:

1. Read the latest `products/{productId}` document.
2. Compute `available = stockTotal - stockReserved`.
3. Reject if requested quantity is invalid or greater than available.
4. Increment `stockReserved`.
5. Create `reservas/{reservationId}` with a price and quantity snapshot.

This avoids overselling even when many buyers tap reserve at the same moment.

Status lifecycle:

- `held`: stock is temporarily reserved.
- `confirmed`: seller accepted and commission can be captured.
- `expired`: hold timed out and stock is released.
- `cancelled`: buyer or seller cancelled and stock is released.
- `fulfilled`: transaction completed and review prompt is shown.

Cloud Functions should run scheduled expiration every minute, release expired holds, send FCM updates and write analytics events.

## Marketplace ranking

The home experience should rank products by:

- Distance to buyer.
- Freshness and recency.
- Available stock and scarcity.
- Seller rating and response speed.
- User behavior: category views, favorites, reservations and shares.
- Paid boost multiplier with quality caps.

The ranking should avoid pure pay-to-win. Boosts amplify good listings; they should not rescue low-trust sellers.

## Growth loops

- Share product deep links with UTM/referral codes.
- Invite friends after first reservation and after a seller gets first sale.
- Notify buyers about new products within their radius.
- Notify buyers when favorite products are almost gone.
- Ask sellers to restock after inventory reaches zero.
- Prompt reviews after fulfilled reservations to compound trust.

## Monetization

- Commission per confirmed reservation: 5-10%.
- Featured product boosts for high-intent local placements.
- Shop subscription for advanced catalogue, recurring availability, analytics and lower commission tiers.

## Global readiness

Current app foundation:

- 13 selectable languages.
- Locale-aware currency formatting.
- Multi-currency product prices.
- Multi-unit inventory.
- RTL-ready Android manifest.

Next backend step:

- Add Cloud Functions for reservation expiration, FCM, recommendation events and geohash fanout.
- Add composite Firestore indexes for `category + geohash + createdAt` and `sellerId + updatedAt`.
