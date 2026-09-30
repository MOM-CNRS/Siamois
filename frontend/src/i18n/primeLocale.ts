import { addLocale, locale as setPrimeLocale } from "primereact/api";
import type { Locale } from "./index";

// PrimeReact ships English only: its calendar and filters read these from its own locale registry,
// which the app has to fill in for any other language.
const FR = {
  firstDayOfWeek: 1,
  dayNames: ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"],
  dayNamesShort: ["dim", "lun", "mar", "mer", "jeu", "ven", "sam"],
  dayNamesMin: ["D", "L", "M", "M", "J", "V", "S"],
  monthNames: ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"],
  monthNamesShort: ["janv", "févr", "mars", "avr", "mai", "juin", "juil", "août", "sept", "oct", "nov", "déc"],
  today: "Aujourd'hui",
  clear: "Effacer",
  weekHeader: "Sem",
  emptyMessage: "Aucun résultat",
  emptyFilterMessage: "Aucun résultat",
};

export function applyPrimeLocale(locale: Locale): void {
  addLocale("fr", FR);
  setPrimeLocale(locale);
}
