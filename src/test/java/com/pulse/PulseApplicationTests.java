package com.pulse.pulse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.pulse.repository.HospitalRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.model.Hospital;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class PulseApplicationTests {

	@Autowired
	private HospitalRepository hospitalRepository;

	@Autowired
	private StockEntryRepository stockEntryRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void findByDistrictReturnsCorrectHospitals() {
		List<Hospital> kozhikodeHospitals = hospitalRepository.findByDistrict("Kozhikode");
		System.out.println("Found " + kozhikodeHospitals.size() + " hospitals in Kozhikode");
		assertEquals(2, kozhikodeHospitals.size());
	}

	@Test
	void searchPublicStockReturnsJoinedResults() {
		List<Object[]> results = stockEntryRepository.searchPublicStock("Paracetamol");
		System.out.println("Found " + results.size() + " results for Paracetamol");
		for (Object[] row : results) {
			System.out.println("Hospital: " + row[0] + ", District: " + row[1] + ", Medicine: " + row[2] + ", Qty: " + row[3] + ", Threshold: " + row[4]);
		}
		assertFalse(results.isEmpty());
	}
}