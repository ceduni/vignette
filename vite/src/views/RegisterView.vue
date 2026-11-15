<script setup>
import {computed, ref} from "vue";
import {RouterLink, useRouter} from "vue-router";
import {register} from "../api/auth";
import BaseAlert from "../components/ui/BaseAlert.vue";
import {useToast} from "../composables/useToast";

const router = useRouter();
const toast = useToast();

const form = ref({
  username: "",
  email: "",
  password: "",
});

const error = ref("");
const loading = ref(false);
const success = ref("");

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const usernameError = computed(() => {
  if (!form.value.username.trim()) return "Username is required.";
  if (form.value.username.trim().length < 3) return "Minimum 3 characters.";
  return "";
});

const emailError = computed(() => {
  if (!form.value.email.trim()) return "Email is required.";
  if (!emailRegex.test(form.value.email.trim())) return "Invalid email address.";
  return "";
});

const passwordError = computed(() => {
  if (!form.value.password) return "Password is required.";
  if (form.value.password.length < 8) return "Minimum 8 characters.";
  return "";
});

const isFormValid = computed(() => {
  return !usernameError.value && !emailError.value && !passwordError.value;
});

async function submit() {
  if (!isFormValid.value) {
    error.value =
        usernameError.value || emailError.value || passwordError.value;
    return;
  }

  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    await register({
      username: form.value.username.trim(),
      email: form.value.email.trim(),
      password: form.value.password,
    });

    success.value = "Account created successfully. You can now log in.";
    toast.success("Account created successfully.");

    setTimeout(() => {
      router.push("/login");
    }, 700);
  } catch (e) {
    error.value = e.message;
    toast.error(e.message || "Registration failed.");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="page auth-page">
    <section class="form-card auth-card auth-card--premium">
      <div class="section-heading">
        <div>
          <h1>Create your account</h1>
          <p class="muted">Set up your Vignette researcher profile.</p>
        </div>
      </div>

      <form @submit.prevent="submit" class="form-stack">
        <label>
          Username
          <input v-model="form.username" autocomplete="username"/>
          <span v-if="form.username && usernameError" class="field-error">
            {{ usernameError }}
          </span>
        </label>

        <label>
          Email
          <input v-model="form.email" type="email" autocomplete="email"/>
          <span v-if="form.email && emailError" class="field-error">
            {{ emailError }}
          </span>
        </label>

        <label>
          Password
          <input v-model="form.password" type="password" autocomplete="new-password"/>
          <span v-if="form.password && passwordError" class="field-error">
            {{ passwordError }}
          </span>
        </label>

        <button type="submit" :disabled="loading || !isFormValid" class="btn btn--primary">
          {{ loading ? "Creating..." : "Create account" }}
        </button>

        <BaseAlert v-if="success" type="success">
          {{ success }}
        </BaseAlert>

        <BaseAlert v-if="error" type="error">
          {{ error }}
        </BaseAlert>

        <p class="muted">
          Already registered?
          <RouterLink to="/login">Go to login</RouterLink>
        </p>
      </form>
    </section>
  </main>
</template>