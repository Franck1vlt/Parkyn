import { Component, signal } from '@angular/core';
import { HeaderComponent } from '../../../../core/components/header/header.component';
import { Page } from '../../../../core/models/page.model';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UserResponse, UsersService } from '../../../../core/services/users.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Role } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-details-user',
  standalone: true,
  imports: [HeaderComponent, ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './details-user.component.html',
  styleUrl: './details-user.component.css',
})
export class DetailsUserComponent {
  page = signal<Page<UserResponse> | undefined>(undefined);
  currentPage = signal(0);
  selectedStatus = signal<string | null>(null);
  isLoading = signal(false);
  user = signal<UserResponse | undefined>(undefined);
  showDeleteModal = signal(false);
  showUpdateModal = signal(false);
  userUpdateForm: FormGroup;
  showPasswordModal = signal(false);
  passwordForm: FormGroup;
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private route: ActivatedRoute,
    private userService: UsersService,
    private fb: FormBuilder,
    private router: Router,
  ) {
    this.userUpdateForm = this.fb.group({
      firstname: [''],
      lastname: [''],
      email: [''],
      role: [''],
    });
    this.passwordForm = this.fb.group(
      {
        password: [''],
        confirmPassword: [''],
      },
      {
        validators: [this.passwordMatchValidator.bind(this)],
      },
    );
  }

  ngOnInit() {
    this.loadUsers();
    const id = this.route.snapshot.paramMap.get('id')!;
    this.userService.getUserById(id).subscribe((u) => this.user.set(u));
  }

  getRoleLabel(role: Role): string {
    const labels: Record<Role, string> = {
      ADMIN: 'Administrateur',
      AGENT: 'Agent',
      DRIVER: 'Conducteur',
    };
    return labels[role];
  }

  getRoleClass(role: Role): string {
    const classes: Record<Role, string> = {
      ADMIN: 'text-purple-700',
      AGENT: 'text-blue-700',
      DRIVER: 'text-green-700',
    };
    return classes[role];
  }

  getStatusLabel(active: boolean): string {
    return active ? 'Actif' : 'Inactif';
  }

  getStatusClass(active: boolean): string {
    return active ? 'text-green-700' : 'text-red-500';
  }

  loadUsers() {
    this.isLoading.set(true);
    this.userService.getUsers(this.currentPage()).subscribe((page) => {
      this.page.set(page);
      this.isLoading.set(false);
    });
  }

  deleteUser(id: string) {
    this.userService.deleteUserById(id).subscribe(() => {
      this.showDeleteModal.set(false);
      this.router.navigate(['/users']);
    });
  }

  onChangePassword() {
    if (this.passwordForm.invalid) return;
    const id = this.user()!.id;
    this.userService
      .updateUserById(id, {
        password: this.passwordForm.value.password,
      })
      .subscribe({
        next: () => {
          this.showPasswordModal.set(false);
          this.passwordForm.reset();
        },
      });
  }

  passwordMatchValidator(form: AbstractControl) {
    const p = form.get('password')?.value;
    const c = form.get('confirmPassword')?.value;
    return p === c ? null : { passwordMismatch: true };
  }

  openUpdateModal() {
    const u = this.user();
    if (!u) return;
    this.userUpdateForm.patchValue({
      firstname: u.firstname,
      lastname: u.lastname,
      email: u.email,
      role: u.role,
    });
    this.showUpdateModal.set(true);
  }

  onSubmit() {
    if (this.userUpdateForm.invalid) return;
    const id = this.user()!.id;
    this.userService.updateUserById(id, this.userUpdateForm.value).subscribe({
      next: (updatedUser) => {
        this.user.set(updatedUser);
        this.showUpdateModal.set(false);
      },
    });
  }
}
