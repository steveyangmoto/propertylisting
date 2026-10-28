"""
Provided reference API for the Listing Search Service coding assessment.

Run with:  python3 server.py [port]
No third-party dependencies - standard library only. Defaults to port 8000,
listens on 0.0.0.0 so it's reachable from an Android emulator (10.0.2.2) or
a physical device on the same network (your machine's LAN IP).
"""
import json
import math
import sys
from datetime import datetime, date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

with open("data.json") as f:
    _data = json.load(f)

LISTINGS = _data["listings"]
RENTALS = _data["rentals"]


def _parse_date(s):
    return datetime.strptime(s, "%Y-%m-%d").date()


def score_listing(listing, target_budget):
    price = listing["price"]
    if target_budget and target_budget > 0:
        price_component = max(0.0, 1 - abs(price - target_budget) / max(target_budget, 1))
    else:
        price_component = 0.5
    days_since = (date.today() - _parse_date(listing["listedDate"])).days
    recency_component = max(0.0, 1 - days_since / 60)
    return round(0.7 * price_component + 0.3 * recency_component, 4)


def score_rental(rental):
    return round(rental["transitScore"] / 100, 4)


def _paginate(items, page, page_size):
    total_results = len(items)
    total_pages = max(1, math.ceil(total_results / page_size))
    start = (page - 1) * page_size
    end = start + page_size
    return items[start:end], total_results, total_pages


def _error(handler, status, message):
    body = json.dumps({"error": message}).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def _ok(handler, payload):
    body = json.dumps(payload).encode("utf-8")
    handler.send_response(200)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        if path == "/health":
            return _ok(self, {"status": "ok"})
        if path == "/listings/search":
            return self.handle_listings_search(qs)
        if path == "/listings/rentals/search":
            return self.handle_rentals_search(qs)
        return _error(self, 404, "Not found")

    def _int_param(self, qs, name, default):
        if name not in qs:
            return default
        try:
            return int(qs[name][0])
        except ValueError:
            return None

    def _float_param(self, qs, name):
        if name not in qs:
            return None
        try:
            return float(qs[name][0])
        except ValueError:
            return None

    def handle_listings_search(self, qs):
        min_price = self._float_param(qs, "minPrice")
        max_price = self._float_param(qs, "maxPrice")
        min_bedrooms = self._int_param(qs, "minBedrooms", None)
        city = qs.get("city", [None])[0]
        keyword = qs.get("keyword", [None])[0]
        target_budget = self._float_param(qs, "targetBudget") or 0
        amenities_param = qs.get("amenities", [None])[0]

        page = self._int_param(qs, "page", 1)
        page_size = self._int_param(qs, "pageSize", 10)

        if page is None or page_size is None or page < 1 or page_size < 1:
            return _error(self, 400, "page and pageSize must be positive integers")
        if min_price is not None and max_price is not None and min_price > max_price:
            return _error(self, 400, "minPrice must be <= maxPrice")

        required_amenities = []
        if amenities_param:
            required_amenities = [a.strip() for a in amenities_param.split(",") if a.strip()]

        results = []
        for listing in LISTINGS:
            if min_price is not None and listing["price"] < min_price:
                continue
            if max_price is not None and listing["price"] > max_price:
                continue
            if min_bedrooms is not None and listing["bedrooms"] < min_bedrooms:
                continue
            if city and listing["city"].lower() != city.lower():
                continue
            if keyword:
                haystack = f"{listing['address']} {listing['description']}".lower()
                if keyword.lower() not in haystack:
                    continue
            if required_amenities:
                listing_amenities = listing.get("amenities") or {}
                if not all(listing_amenities.get(a) is True for a in required_amenities):
                    continue
            enriched = dict(listing)
            enriched["score"] = score_listing(listing, target_budget)
            results.append(enriched)

        results.sort(key=lambda x: x["score"], reverse=True)
        page_items, total_results, total_pages = _paginate(results, page, page_size)

        return _ok(self, {
            "results": page_items,
            "page": page,
            "pageSize": page_size,
            "totalResults": total_results,
            "totalPages": total_pages,
        })

    def handle_rentals_search(self, qs):
        max_rent = self._float_param(qs, "maxRent")
        min_transit_score = self._float_param(qs, "minTransitScore")
        city = qs.get("city", [None])[0]
        keyword = qs.get("keyword", [None])[0]

        page = self._int_param(qs, "page", 1)
        page_size = self._int_param(qs, "pageSize", 10)

        if page is None or page_size is None or page < 1 or page_size < 1:
            return _error(self, 400, "page and pageSize must be positive integers")

        results = []
        for rental in RENTALS:
            if max_rent is not None and rental["monthlyRent"] > max_rent:
                continue
            if min_transit_score is not None and rental["transitScore"] < min_transit_score:
                continue
            if city and rental["city"].lower() != city.lower():
                continue
            if keyword:
                haystack = f"{rental['address']} {rental['description']}".lower()
                if keyword.lower() not in haystack:
                    continue
            enriched = dict(rental)
            enriched["score"] = score_rental(rental)
            results.append(enriched)

        results.sort(key=lambda x: x["score"], reverse=True)
        page_items, total_results, total_pages = _paginate(results, page, page_size)

        return _ok(self, {
            "results": page_items,
            "page": page,
            "pageSize": page_size,
            "totalResults": total_results,
            "totalPages": total_pages,
        })


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"Listening on http://0.0.0.0:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
