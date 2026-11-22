import unittest
from market_models import classic_pivots, detect_sweep, normalize_market_update

class MarketModelsTest(unittest.TestCase):
    def test_market_fields(self):
        d=normalize_market_update({"symbol":"nifty","price":{"ltp":22450,"openInterest":1234},
          "depth":{"buy":[{"price":22449,"qty":10}]},"greeks":{"delta":0.52,"iv":14.2},
          "straddle":{"close":{"ce":120,"pe":110,"spotPrice":22450}}})
        self.assertEqual(d["symbol"],"NIFTY")
        self.assertEqual(d["price"]["ltp"],22450)
        self.assertEqual(d["depth"]["buy"][0]["quantity"],10)
        self.assertEqual(d["greeks"]["delta"],0.52)
        self.assertEqual(d["straddle"]["close"]["ce"],120)
    def test_pivots(self):
        p=classic_pivots(110,90,100)
        self.assertAlmostEqual(p["P"],100)
        self.assertIn("R5",p); self.assertIn("S5",p)
    def test_sweeps(self):
        self.assertEqual(detect_sweep(100,90,{"high":102,"low":95,"close":99}),["HIGH_SWEEP_REJECTION"])
        self.assertEqual(detect_sweep(100,90,{"high":98,"low":88,"close":92}),["LOW_SWEEP_RECLAIM"])

if __name__=="__main__": unittest.main()
