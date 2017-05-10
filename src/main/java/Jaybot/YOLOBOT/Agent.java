package Jaybot.YOLOBOT;

import core.game.StateObservation;
import core.player.AbstractPlayer;
import Jaybot.YOLOBOT.SubAgents.BreitenSucheAgent;
import Jaybot.YOLOBOT.SubAgents.HandleMCTS.MCTHandler;
import Jaybot.YOLOBOT.SubAgents.SubAgent;
import Jaybot.YOLOBOT.SubAgents.SubAgentStatus;
import Jaybot.YOLOBOT.SubAgents.bfs.BFS;
import Jaybot.YOLOBOT.Util.Heatmap;
import Jaybot.YOLOBOT.Util.Heuristics.HeuristicList;
import Jaybot.YOLOBOT.Util.Wissensdatenbank.YoloKnowledge;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class Agent extends AbstractPlayer {

	public final static boolean UPLOAD_VERSION = false;
	public final static boolean DRAW_TARGET_ONLY = false;
	public final static boolean FORCE_PAINT = false;

	private final List<SubAgent> subAgents;
	private SubAgent currentSubAgent;
	public static ElapsedCpuTimer curElapsedTimer;
	
	//CheckVariablen um ersten Schritt-Bug zu umgehen:
	private int avatarXSpawn = -1, avatarYSpawn = -1;
	private StateObservation lastStateObs;
	public static YoloState currentYoloState;

	public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer) {
		curElapsedTimer = elapsedTimer;
		YoloState startYoloState = new YoloState(so);
		avatarXSpawn = startYoloState.getAvatarX();
		avatarYSpawn = startYoloState.getAvatarY();
		//YoloKnowledge und sonstiges Wissen hier generieren
    	YoloKnowledge.instance = new YoloKnowledge(startYoloState);
		Heatmap.instance = new Heatmap(startYoloState);
		HeuristicList.instance = new HeuristicList();

		// Liste von SubAgents wird hier stellt
		BFS bfs = new BFS(startYoloState, elapsedTimer);
		BreitenSucheAgent bsa = new BreitenSucheAgent(startYoloState,
				elapsedTimer);
		subAgents = new LinkedList<>();
		subAgents.add(bsa);

		subAgents.add(new MCTHandler(startYoloState));
		subAgents.add(bfs);

		bfs.preRun(startYoloState, elapsedTimer);

		if (!Agent.UPLOAD_VERSION)
			System.out.println(YoloKnowledge.instance.toString());
	}

	@Override
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		curElapsedTimer = elapsedTimer;
		currentYoloState = new YoloState(stateObs);
		YoloKnowledge.instance.learnStochasticEffekts(currentYoloState);

		if(currentYoloState.getGameTick() == 1){
			//Ist erster Tick nach Spielstart:
			//Ueberpruefe, ob Erster-Schritt-Bug aufgetreten ist:
			if(ersterSchrittBugAufgetreten(currentYoloState)){
				return currentYoloState.getAvatarLastAction();
			}
		}

		// TODO: Hier koennten allgemeine, agentunabhaengige Auswertungen
		// geschehen, welche den aktuellen SubAgent auswaehlen
		YoloState.currentGameScore = currentYoloState.getGameScore();
		Heatmap.instance.stepOn(currentYoloState);

		checkIfAndDoAgentChange();

		if (!Agent.UPLOAD_VERSION)
			System.out.println("Chosen Agent: "
					+ currentSubAgent.getClass().getName());

		ACTIONS chosenAction = currentSubAgent.act(currentYoloState,
				elapsedTimer);

		if (chosenAction == ACTIONS.ACTION_NIL
				&& currentSubAgent.Status == SubAgentStatus.POSTPONED) {
			// Old agent give up!
			if (elapsedTimer.remainingTimeMillis() > 10) {
				// If we have time for another agent run, do so:
				checkIfAndDoAgentChange();
				chosenAction = currentSubAgent.act(currentYoloState,
						elapsedTimer);
			}
		}

		if (!Agent.UPLOAD_VERSION) {
			System.out.println("\t Chosen Action: " + chosenAction.toString());
			System.out.println("Advance Steps used: "
					+ YoloState.advanceCounter);
			System.out.println("Time remaining: "
					+ elapsedTimer.remainingTimeMillis());

			String dynamics = "Dynamic Objects:";
			for (int i = 0; i < 32; i++) {
				if (YoloKnowledge.instance.isDynamic(i)) {
					dynamics += "\t " + YoloKnowledge.instance.indexToItype(i);
				}
			}
			System.out.println(dynamics);

		}
		YoloState.advanceCounter = 0;

		return chosenAction;
	}

	private void checkIfAndDoAgentChange() {
		// Pruefe, ob ein neuer SubAgent ausgesucht werden muss
		if (currentSubAgent == null
				|| currentSubAgent.Status != SubAgentStatus.IN_PROGRESS) {
			currentSubAgent = ChooseNewIdleSubAgent(currentYoloState);

			// Falls kein Agent bereit ist, setze erneut alle auf Status "IDLE"
			// und suche erneut nach einem neuen Agent
			if (currentSubAgent == null) {
				for (SubAgent subAgent : subAgents) {
					subAgent.Status = SubAgentStatus.IDLE;
				}
				currentSubAgent = ChooseNewIdleSubAgent(currentYoloState);
			}

			currentSubAgent.Status = SubAgentStatus.IN_PROGRESS;
		}
	}

	private boolean ersterSchrittBugAufgetreten(YoloState yoloState) {
		boolean error = true;
		error &= yoloState.getGameTick() == 1;
		error &= yoloState.getAvatarOrientation().equals(
				YoloKnowledge.ORIENTATION_NULL);
		error &= yoloState.getAvatarX() == avatarXSpawn;
		error &= yoloState.getAvatarY() == avatarYSpawn;
		return error;

	}

	/**
	 * Finde einen neuen Agent mit Status "IDLE" und maximalem Gewicht
	 */
	private SubAgent ChooseNewIdleSubAgent(YoloState yoloState) {
		SubAgent newAgent = null;

		double maxWeight = -Double.MAX_VALUE;
		for (SubAgent subAgent : subAgents) {
			if (subAgent.Status == SubAgentStatus.IDLE) {
				double subAgentWeight = subAgent.EvaluateWeight(yoloState);

				if (maxWeight < subAgentWeight) {
					maxWeight = subAgentWeight;
					newAgent = subAgent;
				}
			}
		}

		return newAgent;
	}
	
	@Override
	public void draw(Graphics2D g) {
		if(Agent.UPLOAD_VERSION && !FORCE_PAINT)
			return;
		try {
			if(currentSubAgent != null){
					currentSubAgent.draw(g);
			}
			
			if(currentYoloState == null)
				return;
			
			//Draw KillByStochastic:
			int block_size = currentYoloState.getBlockSize();
			int half_block = (int) (block_size * 0.5);

			g.setColor(Color.black);

			for (int j = 0; j < currentYoloState.getObservationGrid()[0].length; ++j) {
				for (int i = 0; i < currentYoloState.getObservationGrid().length; ++i) {
					
					//Draw TOD:
					String print = "";
					if(!Agent.DRAW_TARGET_ONLY)
						if(YoloKnowledge.instance.canBeKilledByStochasticEnemyAt(currentYoloState, i,j))
							print = "TOD";
					
					if(!Agent.DRAW_TARGET_ONLY)
						g.drawString(print, i * block_size, j * block_size +
							 half_block+12 );
					
					//Draw (stupid) raster:
					if(!Agent.DRAW_TARGET_ONLY)
						g.drawRect(i * block_size, j * block_size, block_size, block_size);
				}
			}
		} catch (Exception e) {
		}
	}

}
