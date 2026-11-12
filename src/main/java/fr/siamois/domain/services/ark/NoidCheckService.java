package fr.siamois.domain.services.ark;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Uses the NOID CHECK DIGIT ALGORITHM to create control character</p>
 *
 * @author Julien Linget
 * @see <a href="https://metacpan.org/dist/Noid/view/noid#NOID-CHECK-DIGIT-ALGORITHM">NOID CHECK DIGIT ALGORITHM</a>
 */
@Service
public class NoidCheckService {

    private static final Map<Character, Integer> refMap;

    static {
        refMap = new HashMap<>();
        int counter = 0;
        for (char c : "0123456789bcdfghjklmnpqrstvwxyz".toCharArray()) {
            refMap.put(c, counter++);
        }
    }

    /**
     * Calculates the check digit for a given identifier using the NOID CHECK DIGIT ALGORITHM.
     *
     * @param identifier the identifier for which to calculate the check digit
     * @return the check digit as a character
     */
    public String calculateCheckDigit(String identifier) {
        char[] idArray = identifier.toLowerCase().toCharArray();
        int sum = 0;
        for (int index = 0; index < idArray.length; index++) {
            sum += refMap.getOrDefault(idArray[index], 0) * (index + 1);
        }
        int remainder = sum % 30;

        return getCharOfIntValue(remainder) + "";
    }

    private char getCharOfIntValue(int value) {
        for (Map.Entry<Character, Integer> entry : refMap.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("The given integer should be between 0 and 30 excluded");
    }

}
