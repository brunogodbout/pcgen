/*
 * AbilityUtilities.java
 * Copyright 2001 (C) Bryan McRoberts <merton_monk@yahoo.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.     See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Created on Aug 25, 2005
 *  Refactored from PlayerCharacter, created on April 21, 2001, 2:15 PM
 *
 *
 */
package pcgen.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import pcgen.cdom.base.ChooseInformation;
import pcgen.cdom.base.UserSelection;
import pcgen.cdom.content.CNAbility;
import pcgen.cdom.enumeration.Nature;
import pcgen.cdom.enumeration.ObjectKey;
import pcgen.cdom.helper.CNAbilitySelection;
import pcgen.core.chooser.ChoiceManagerList;
import pcgen.core.chooser.ChooserUtilities;
import pcgen.core.utils.CoreUtility;
import pcgen.core.utils.LastGroupSeparator;
import pcgen.core.utils.LastGroupSeparator.GroupingMismatchException;

/**
 * General utilities related to the Ability class.
 *
 * @author   Bryan McRoberts <merton_monk@users.sourceforge.net>
 * @version  $Revision$
 */
public class AbilityUtilities
{
	private AbilityUtilities ()
	{
		// private constructor, do nothing
	}

	public static void adjustPool(final Ability ability,
			final PlayerCharacter aPC, final boolean addIt,
			double abilityCount, boolean removed)
	{
		if (!addIt && !ability.getSafe(ObjectKey.MULTIPLE_ALLOWED) && removed)
		{
			// We don't need to adjust the pool for abilities here as it is recalculated each time it is queried.
			abilityCount += ability.getSafe(ObjectKey.SELECTION_COST).doubleValue();
		}
		else if (addIt && !ability.getSafe(ObjectKey.MULTIPLE_ALLOWED))
		{
			abilityCount -= ability.getSafe(ObjectKey.SELECTION_COST).doubleValue();
		}
		else
		{
			int listSize = aPC.getSelectCorrectedAssociationCount(ability);

			for (CNAbility cna : aPC.getPoolAbilities(AbilityCategory.FEAT, Nature.NORMAL))
			{
				if (cna.getAbilityKey().equalsIgnoreCase(ability.getKeyName()))
				{
					listSize = aPC.getSelectCorrectedAssociationCount(cna);
				}
			}

			abilityCount -= (listSize * ability.getSafe(ObjectKey.SELECTION_COST).doubleValue());
		}
		aPC.adjustAbilities(AbilityCategory.FEAT, BigDecimal.valueOf(abilityCount));
	}

	public static Ability retrieveAbilityKeyed(AbilityCategory aCat,
			final String token)
	{
		Ability ability = Globals.getContext().ref
				.silentlyGetConstructedCDOMObject(Ability.class, aCat, token);

		if (ability != null)
		{
			return ability;
		}

		final String stripped = removeChoicesFromName(token);
		ability = Globals.getContext().ref.silentlyGetConstructedCDOMObject(
				Ability.class, aCat, stripped);

		if (ability != null)
		{
			return ability;
		}

		return null;
	}

	/**
	 * Extracts the choiceless form of a name, for example, with all choices removed
	 *
	 * @param name
	 *
	 * @return the name with sub-choices stripped from it
	 */
	public static String removeChoicesFromName(String name)
	{
		LastGroupSeparator lgs = new LastGroupSeparator(name);
		lgs.process();
		return lgs.getRoot().trim();
	}

	/**
	 * Takes a string of the form "foo (bar, baz)", populates the array with ["bar", "baz"]
	 * and returns foo.  All strings returned by this function have had leading.trailing
	 * whitespace removed.
	 *
	 * @param name      The full name with stuff in parenthesis
	 * @param specifics a list which will contain the specifics after the operation has
	 *                  completed
	 *
	 * @return the name with sub-choices stripped from it
	 * @throws GroupingMismatchException If there are mismatched brackets
	 */
	public static String getUndecoratedName(
			final String name, 
			final Collection<String> specifics) throws GroupingMismatchException
	{
		LastGroupSeparator lgs = new LastGroupSeparator(name);
		String subName = lgs.process();
		String altName = lgs.getRoot();

		specifics.clear();
		// we want what is inside the outermost parenthesis.
		if (subName != null)
		{
			specifics.addAll(CoreUtility.split(subName, ','));
		}
		return altName.trim();
	}

	/**
	 * Whether an association has already been selected for this PC.
	 * to the associated list of this
	 *
	 * @return true if the association has already been selected
	 */
	public static boolean alreadySelected(PlayerCharacter pc, Ability ability,
		String selection, boolean allowStack)
	{
		Collection<CNAbility> cnAbilities = pc.getMatchingCNAbilities(ability);
		if (cnAbilities.isEmpty())
		{
			//Don't have any form of it
			return false;
		}
		if (!ability.getSafe(ObjectKey.MULTIPLE_ALLOWED))
		{
			//Based on key name / category match
			return true;
		}
		if (allowStack && ability.getSafe(ObjectKey.STACKS))
		{
			//Must allow it because it stacks
			return false;
		}
		ChooseInformation<?> info = ability.get(ObjectKey.CHOOSE_INFO);
		Object decoded =
				info.decodeChoice(Globals.getContext(), selection);
		for (CNAbility cna : cnAbilities)
		{
			List<?> oldSelections = pc.getDetailedAssociations(cna);
			if ((oldSelections != null) && oldSelections.contains(decoded))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Identify if the object passed in is a feat.
	 * @param obj The object to be checked.
	 * @return true if this is a feat, false if not.
	 */
	public static boolean isFeat(Object obj)
	{
		if (!(obj instanceof Ability))
		{
			return false;
		}
		Ability ability = (Ability) obj;
		if (ability.getCDOMCategory() == null)
		{
			return false;
		}
		return ability.getCDOMCategory() == AbilityCategory.FEAT
			|| (ability.getCDOMCategory().getParentCategory() == AbilityCategory.FEAT);
	}

	public static Ability validateCNAList(List<CNAbility> list)
	{
		Ability a = null;
		for (CNAbility cna : list)
		{
			if (a == null)
			{
				a = cna.getAbility();
			}
			else
			{
				if (!cna.getAbility().getKeyName().equals(a.getKeyName())
					|| !a.getCDOMCategory().equals(
						cna.getAbilityCategory().getParentCategory()))
				{
					throw new IllegalArgumentException(
						"CNAbility list must be a consistent list of Abilities (same object)");
				}
			}
		}
		return a;
	}

	public static void driveChooseAndAdd(CNAbility cna, PlayerCharacter pc,
		boolean toAdd)
	{
		Ability ability = cna.getAbility();
		if (!ability.getSafe(ObjectKey.MULTIPLE_ALLOWED))
		{
			CNAbilitySelection cnas = new CNAbilitySelection(cna);
			if (toAdd)
			{
				pc.addAbility(cnas, UserSelection.getInstance(),
					UserSelection.getInstance());
			}
			else
			{
				pc.removeAbility(cnas, UserSelection.getInstance(),
					UserSelection.getInstance());
			}
		}
		AbilityCategory category = (AbilityCategory) cna.getAbilityCategory();
		// how many sub-choices to make
		ArrayList<String> reservedList = new ArrayList<String>();

		ChoiceManagerList<?> aMan =
				ChooserUtilities.getConfiguredController(ability, pc, category,
					reservedList);
		if (aMan != null)
		{
			processSelection(pc, cna, aMan, toAdd);
			return;
		}
		//TODO Log error? (or MULT:NO?)
	}

	private static <T> void processSelection(
		PlayerCharacter pc, CNAbility cna, ChoiceManagerList<T> aMan, boolean toAdd)
	{
		ArrayList<T> availableList = new ArrayList<T>();
		ArrayList<T> selectedList = new ArrayList<T>();
		aMan.getChoices(pc, availableList, selectedList);

		if (availableList.size() == 0 && selectedList.size() == 0)
		{
			//TODO Log error? (ignored choice?)
			return;
		}

		List<T> origSelections = new ArrayList<T>(selectedList);
		List<T> removedSelections = new ArrayList<T>(selectedList);
		ArrayList<String> reservedList = new ArrayList<String>();

		List<T> newSelections;
		if (toAdd)
		{
			newSelections =
					aMan.doChooser(pc, availableList, selectedList,
						reservedList);
		}
		else
		{
			newSelections =
					aMan.doChooserRemove(pc, availableList, selectedList,
						reservedList);
		}

		//Need to use only the new ones
		removedSelections.removeAll(newSelections);
		newSelections.removeAll(origSelections);

		for (T sel : newSelections)
		{
			String selection = aMan.encodeChoice(sel);
			CNAbilitySelection cnas = new CNAbilitySelection(cna, selection);
			pc.addAbility(cnas, UserSelection.getInstance(),
				UserSelection.getInstance());
		}
		for (T sel : removedSelections)
		{
			String selection = aMan.encodeChoice(sel);
			CNAbilitySelection cnas = new CNAbilitySelection(cna, selection);
			pc.removeAbility(cnas, UserSelection.getInstance(),
				UserSelection.getInstance());
		}
	}
}
