package Jaybot.YOLOBOT.Util.RandomForest;

import Jaybot.YOLOBOT.Util.Wissensdatenbank.YoloEvent;
import Jaybot.YOLOBOT.YoloState;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Torsten on 18.05.2017.
 */
public class RandomForest {

    private RandomTree[] forest;
    private int treeSize;

    public RandomForest(int treeSize, int forestSize) {
        this.treeSize = treeSize;
        forest = new RandomTree[forestSize];

        for (int treeIndex = 0; treeIndex < forestSize; treeIndex++) {
            forest[treeIndex] = new RandomTree(treeSize);
        }
    }

    public void train(int colliderIType, byte[][] inventory, YoloEvent[] events) {
        if (inventory == null || events == null || inventory.length != events.length)
            throw new IllegalArgumentException("The inventory and event arrays have to be the same length");

        for (int i = 0; i < inventory.length; i++) {
            train(colliderIType, inventory[i], events[i]);
        }
    }

    public void train(int colliderIType, byte[] inventory, YoloEvent event) {
        for (RandomTree tree : forest) {
            tree.train(colliderIType, inventory, event);
        }
    }

    public YoloEvent getEvent(int colliderIType, byte[] inventory) {
        Map<YoloEvent, Integer> eventFrequencies = new HashMap<>();
        int maxFrequency = -1;
        YoloEvent maxYoloEvent = new YoloEvent();

        for (RandomTree tree : forest) {
            YoloEvent event = tree.getEvent(colliderIType, inventory);

            if (event == null)
                continue;

            int frequency = 1;
            if (eventFrequencies.containsKey(event)) {
                frequency = eventFrequencies.get(event) + 1;
            }
            eventFrequencies.put(event, frequency);

            if (frequency > maxFrequency) {
                maxFrequency = frequency;
                maxYoloEvent = event;
            }
        }

        return maxYoloEvent;
    }

    @Override
    public String toString() {
        boolean addComma = false;
        StringBuilder sb = new StringBuilder("{ \"forest\" : [\n");

        for (RandomTree tree : forest) {
            sb.append((addComma ? ", \n" : ""));
            sb.append(tree.toString());
            addComma = true;
        }

        sb.append("]}");
        return sb.toString();
    }

    public RandomTree[] getTrees() {
        return forest;
    }
}