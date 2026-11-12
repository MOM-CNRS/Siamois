import {useState} from "react";
import {InputText} from "primereact/inputtext";
import {CreateFormField, CreateFormShell} from "../components/create/CreateFormShell";
import {createPersonAwaitingAccount, type FilterOption, type PersonNameInput} from "./optionSources";
import {messageForError} from "../api/errors";
import {t} from "../i18n";

export interface CreatePersonFormProps {
  // The project the person is named in: they join its members.
  projectId: string;
  // What was typed in the picker, already split into a first and a last name.
  initial: PersonNameInput;
  onCreated: (person: FilterOption) => void;
  onCancel: () => void;
}

/**
 * « Nouvelle personne » from a field expecting a person: someone without an account, created from their
 * first and last name only. They join the project's members and await the creation of their account,
 * which a manager can later link to them (member dialog).
 */
export function CreatePersonForm({ projectId, initial, onCreated, onCancel }: Readonly<CreatePersonFormProps>) {
  const [name, setName] = useState(initial.name);
  const [lastname, setLastname] = useState(initial.lastname);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canSubmit = !pending && name.trim() !== "" && lastname.trim() !== "";

  async function submit() {
    setPending(true);
    setError(null);
    try {
      onCreated(await createPersonAwaitingAccount(projectId, { name, lastname }));
    } catch (e) {
      setError(messageForError(e, t("field.personCreateFailed")));
    } finally {
      setPending(false);
    }
  }

  return (
    <CreateFormShell
      entityType="person"
      icon="bi bi-person-plus"
      title={t("person.create.title")}
      canSubmit={canSubmit}
      pending={pending}
      error={error}
      footerNote={t("person.create.hint")}
      onSubmit={() => void submit()}
      onCancel={onCancel}
    >
      <CreateFormField label={t("person.create.firstName")} required>
        <InputText value={name} autoFocus onChange={(e) => setName(e.target.value)} />
      </CreateFormField>
      <CreateFormField label={t("person.create.lastName")} required>
        <InputText value={lastname} onChange={(e) => setLastname(e.target.value)} />
      </CreateFormField>
    </CreateFormShell>
  );
}
